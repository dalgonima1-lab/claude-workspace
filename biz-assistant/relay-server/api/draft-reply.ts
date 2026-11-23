import type { VercelRequest, VercelResponse } from "@vercel/node";
import { DraftReplyRequestSchema } from "../lib/schema.js";
import { draftReply } from "../lib/claude.js";
import { isAuthorized, json, readBody, requireConfig, sendError } from "../lib/http.js";

export default async function handler(req: VercelRequest, res: VercelResponse): Promise<void> {
  if (req.method !== "POST") return json(res, 405, { error: "POST만 받습니다" });
  const missing = requireConfig();
  if (missing) return json(res, 503, { error: missing });
  if (!isAuthorized(req)) return json(res, 401, { error: "인증 실패" });
  try {
    const input = DraftReplyRequestSchema.parse(readBody(req));
    const text = await draftReply(input);
    return json(res, 200, { reply: text });
  } catch (err) {
    return sendError(res, err);
  }
}
