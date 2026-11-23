import type { VercelRequest, VercelResponse } from "@vercel/node";
import { RefineRequestSchema, refine } from "../lib/refine.js";
import { isAuthorized, json, readBody, requireConfig, sendError } from "../lib/http.js";

/** POST /api/refine : 확인이 필요한 점에 대한 답을 기록에 반영한다 */
export default async function handler(req: VercelRequest, res: VercelResponse): Promise<void> {
  if (req.method !== "POST") return json(res, 405, { error: "POST만 받습니다" });
  const missing = requireConfig();
  if (missing) return json(res, 503, { error: missing });
  if (!isAuthorized(req)) return json(res, 401, { error: "인증 실패" });
  const started = Date.now();
  try {
    const input = RefineRequestSchema.parse(readBody(req));
    const result = await refine(input);
    console.log(JSON.stringify({ event: "refine", answers: input.answers.length, ms: Date.now() - started, remaining: result.remaining_questions.length }));
    return json(res, 200, result);
  } catch (err) {
    return sendError(res, err);
  }
}
