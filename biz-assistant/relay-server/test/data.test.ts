import { test, before, after } from "node:test";
import assert from "node:assert/strict";
import { PGlite } from "@electric-sql/pglite";
import type { Query } from "../lib/db.js";
import { pull, push, counts, resetSchemaCache } from "../lib/data.js";

let db: PGlite;
let q: Query;

before(async () => {
  db = new PGlite();
  q = async (text, params) => (await db.query(text, params ?? [])).rows as Record<string, unknown>[];
  resetSchemaCache();
});
after(async () => { await db.close(); });

test("넣고 전부 받기", async () => {
  const r = await push(q, [
    { kind: "org", uid: "o1", updatedAt: 1000, deleted: false, data: { name: "군자전기사업소", kind: "client" } },
    { kind: "record", uid: "r1", updatedAt: 1000, deleted: false, data: { orgUid: "o1", summary: "조명 제어기 장애" } },
  ], 5000);
  assert.equal(r.applied, 2);
  const all = await pull(q, 0);
  assert.equal(all.items.length, 2);
  assert.equal(all.cursor, 5000);
  assert.deepEqual(all.items.find((i) => i.uid === "o1")?.data, { name: "군자전기사업소", kind: "client" });
});

test("마지막 쓰기 우선: 오래된 버전은 건너뛴다", async () => {
  const older = await push(q, [{ kind: "org", uid: "o1", updatedAt: 500, deleted: false, data: { name: "옛 이름" } }], 6000);
  assert.equal(older.applied, 0);
  const newer = await push(q, [{ kind: "org", uid: "o1", updatedAt: 2000, deleted: false, data: { name: "새 이름" } }], 7000);
  assert.equal(newer.applied, 1);
  const got = await pull(q, 0);
  assert.equal(got.items.find((i) => i.uid === "o1")?.data.name, "새 이름");
});

test("커서 이후 바뀐 것만 받는다", async () => {
  const delta = await pull(q, 6500);
  assert.deepEqual(delta.items.map((i) => i.uid), ["o1"]);
  assert.equal(delta.cursor, 7000);
  const none = await pull(q, 7000);
  assert.equal(none.items.length, 0);
  assert.equal(none.cursor, 7000);
});

test("지운 항목은 표시로 전달되고 개수에서 빠진다", async () => {
  await push(q, [{ kind: "record", uid: "r1", updatedAt: 3000, deleted: true, data: {} }], 8000);
  const delta = await pull(q, 7000);
  assert.equal(delta.items[0].deleted, true);
  const c = await counts(q);
  assert.equal(c.org, 1);
  assert.equal(c.record ?? 0, 0);
});

test("종류 필터", async () => {
  await push(q, [{ kind: "task", uid: "t1", updatedAt: 1, deleted: false, data: { title: "잔금 확인" } }], 9000);
  const onlyTasks = await pull(q, 0, ["task"]);
  assert.deepEqual(onlyTasks.items.map((i) => i.kind), ["task"]);
});
