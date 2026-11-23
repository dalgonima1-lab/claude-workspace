import { z } from "zod";
import type { Query } from "./db.js";

/**
 * 폰과 웹이 함께 쓰는 기록 저장소.
 * 종류(kind)마다 JSON 한 덩어리를 uid로 저장한다. 데이터가 작아서 조회와 정렬은 클라이언트가 한다.
 * 같은 uid는 updatedAt이 큰 쪽이 이긴다(마지막 쓰기 우선). 지운 항목은 deleted=true 로 표시만 한다.
 * 동기화 커서는 서버가 받은 시각(received_at)이라 기기 시계가 틀려도 빠지는 항목이 없다.
 */
export const KINDS = ["org", "project", "record", "event", "task", "money", "ticket", "pending", "setting"] as const;
export type Kind = (typeof KINDS)[number];

export const ItemSchema = z.object({
  kind: z.enum(KINDS),
  uid: z.string().min(1).max(64),
  updatedAt: z.number().int().nonnegative(),
  deleted: z.boolean().default(false),
  data: z.record(z.string(), z.unknown()).default({}),
});
export type Item = z.infer<typeof ItemSchema>;

export const PushRequestSchema = z.object({
  items: z.array(ItemSchema).max(2000),
});

let schemaReady = false;

export async function ensureSchema(q: Query): Promise<void> {
  if (schemaReady) return;
  await q(`CREATE TABLE IF NOT EXISTS items (
    kind text NOT NULL,
    uid text NOT NULL,
    updated_at bigint NOT NULL,
    received_at bigint NOT NULL,
    deleted boolean NOT NULL DEFAULT false,
    data jsonb NOT NULL DEFAULT '{}'::jsonb,
    PRIMARY KEY (kind, uid)
  )`);
  await q(`CREATE INDEX IF NOT EXISTS items_received_at ON items (received_at)`);
  schemaReady = true;
}

/** 테스트에서 스키마 캐시를 되돌릴 때 */
export function resetSchemaCache(): void { schemaReady = false; }

export interface PullResult { items: Item[]; cursor: number; }

/** since(서버 수신 시각, ms) 이후 바뀐 항목. since=0 이면 전부. */
export async function pull(q: Query, since: number, kinds?: Kind[]): Promise<PullResult> {
  await ensureSchema(q);
  const params: unknown[] = [since];
  let where = `received_at > $1`;
  if (kinds && kinds.length) { params.push(kinds); where += ` AND kind = ANY($2::text[])`; }
  const rows = await q(`SELECT kind, uid, updated_at, received_at, deleted, data FROM items WHERE ${where} ORDER BY received_at ASC, kind, uid`, params);
  let cursor = since;
  const items: Item[] = rows.map((r) => {
    const received = Number(r.received_at);
    if (received > cursor) cursor = received;
    return {
      kind: r.kind as Kind,
      uid: String(r.uid),
      updatedAt: Number(r.updated_at),
      deleted: Boolean(r.deleted),
      data: (r.data as Record<string, unknown>) ?? {},
    };
  });
  return { items, cursor };
}

export interface PushResult { received: number; applied: number; cursor: number; }

/** 여러 항목을 한 번에 넣는다. 이미 더 새 버전이 있으면 그 항목은 건너뛴다. */
export async function push(q: Query, items: Item[], now: number = Date.now()): Promise<PushResult> {
  await ensureSchema(q);
  if (items.length === 0) return { received: 0, applied: 0, cursor: now };
  let applied = 0;
  const CHUNK = 200;
  for (let i = 0; i < items.length; i += CHUNK) {
    const chunk = items.slice(i, i + CHUNK);
    const values: string[] = [];
    const params: unknown[] = [];
    chunk.forEach((it, idx) => {
      const base = idx * 6;
      values.push(`($${base + 1}, $${base + 2}, $${base + 3}, $${base + 4}, $${base + 5}, $${base + 6}::jsonb)`);
      params.push(it.kind, it.uid, it.updatedAt, now, it.deleted, JSON.stringify(it.data));
    });
    const rows = await q(
      `INSERT INTO items (kind, uid, updated_at, received_at, deleted, data) VALUES ${values.join(", ")}
       ON CONFLICT (kind, uid) DO UPDATE SET
         updated_at = EXCLUDED.updated_at,
         received_at = EXCLUDED.received_at,
         deleted = EXCLUDED.deleted,
         data = EXCLUDED.data
       WHERE items.updated_at < EXCLUDED.updated_at
       RETURNING uid`,
      params,
    );
    applied += rows.length;
  }
  return { received: items.length, applied, cursor: now };
}

/** 종류별 개수. 웹 화면의 상태 표시와 점검용 */
export async function counts(q: Query): Promise<Record<string, number>> {
  await ensureSchema(q);
  const rows = await q(`SELECT kind, COUNT(*)::int AS n FROM items WHERE deleted = false GROUP BY kind`);
  const out: Record<string, number> = {};
  for (const r of rows) out[String(r.kind)] = Number(r.n);
  return out;
}
