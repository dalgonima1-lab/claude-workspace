import type { VercelRequest, VercelResponse } from "@vercel/node";
import { DatabaseUnavailable, getQuery } from "../lib/db.js";
import { KINDS, PushRequestSchema, counts, pull, push, type Kind } from "../lib/data.js";
import { isAuthorized, json, readBody, requireConfig, sendError } from "../lib/http.js";

/**
 * 폰과 웹이 기록을 주고받는 곳.
 *  GET  /api/data?since=<ms>[&kinds=org,record]  → { items, cursor }
 *  POST /api/data  { items: [...] }               → { received, applied, cursor }
 *  GET  /api/data?stats=1                          → 종류별 개수
 */
export default async function handler(req: VercelRequest, res: VercelResponse): Promise<void> {
  const missing = requireConfig();
  if (missing) return json(res, 503, { error: missing });
  if (!isAuthorized(req)) return json(res, 401, { error: "인증 실패" });
  let q;
  try { q = getQuery(); } catch (e) {
    if (e instanceof DatabaseUnavailable) return json(res, 503, { error: e.message });
    throw e;
  }
  try {
    if (req.method === "GET") {
      if (req.query.stats) return json(res, 200, await counts(q));
      const since = Number(req.query.since ?? 0) || 0;
      const kindsParam = typeof req.query.kinds === "string" ? req.query.kinds : undefined;
      const kinds = kindsParam?.split(",").filter((k): k is Kind => (KINDS as readonly string[]).includes(k));
      return json(res, 200, await pull(q, since, kinds?.length ? kinds : undefined));
    }
    if (req.method === "POST") {
      const body = PushRequestSchema.parse(readBody(req));
      return json(res, 200, await push(q, body.items));
    }
    return json(res, 405, { error: "GET 또는 POST만 받습니다" });
  } catch (err) {
    const msg = err instanceof Error ? err.message : String(err);
    if (msg.includes("fetch failed") || msg.includes("ECONN")) {
      console.error("database connection error:", msg);
      return json(res, 502, { error: "데이터베이스에 연결하지 못했습니다" });
    }
    return sendError(res, err);
  }
}
