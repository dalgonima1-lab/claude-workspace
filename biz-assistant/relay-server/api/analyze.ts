import type { VercelRequest, VercelResponse } from "@vercel/node";
import { AnalyzeRequestSchema } from "../lib/schema.js";
import { analyze } from "../lib/claude.js";
import { isAuthorized, json, readBody, requireConfig, sendError } from "../lib/http.js";

export default async function handler(req: VercelRequest, res: VercelResponse): Promise<void> {
  if (req.method !== "POST") return json(res, 405, { error: "POST만 받습니다" });
  const missing = requireConfig();
  if (missing) return json(res, 503, { error: missing });
  if (!isAuthorized(req)) return json(res, 401, { error: "인증 실패" });

  const started = Date.now();
  try {
    const input = AnalyzeRequestSchema.parse(readBody(req));
    const result = await analyze(input);
    // 원문은 로그에 남기지 않는다. 크기와 소요 시간, 토큰만 기록.
    console.log(
      JSON.stringify({
        event: "analyze",
        source: input.source,
        chars: input.text.length,
        ms: Date.now() - started,
        model: result.model,
        type: result.analysis.type,
        usage: result.usage,
      }),
    );
    return json(res, 200, result);
  } catch (err) {
    return sendError(res, err);
  }
}
