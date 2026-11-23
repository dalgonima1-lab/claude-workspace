import type { VercelRequest, VercelResponse } from "@vercel/node";
import { isAuthorized, json, requireConfig } from "../lib/http.js";
import { transcribe } from "../lib/transcribe.js";

// 음성 파일을 그대로 받기 위해 기본 본문 파서를 끈다.
export const config = { api: { bodyParser: false } };

const MAX_BYTES = 4_400_000; // Vercel 함수 요청 본문 한도(4.5MB) 안쪽

/**
 * POST /api/transcribe
 * 본문: 음성 파일 바이트 (audio/mp4, audio/mpeg, audio/wav 등)
 * 헤더: x-app-token, content-type, 선택 x-caller-hint(상대 이름), x-chunk-index / x-chunk-count
 * 응답: { text, model, seconds? }
 */
export default async function handler(req: VercelRequest, res: VercelResponse): Promise<void> {
  if (req.method !== "POST") return json(res, 405, { error: "POST만 받습니다" });
  const missing = requireConfig();
  if (missing) return json(res, 503, { error: missing });
  if (!process.env.OPENAI_API_KEY) return json(res, 503, { error: "OPENAI_API_KEY 환경 변수가 없습니다" });
  if (!isAuthorized(req)) return json(res, 401, { error: "인증 실패" });

  const started = Date.now();
  try {
    const chunks: Buffer[] = [];
    let total = 0;
    for await (const c of req) {
      const b = Buffer.isBuffer(c) ? c : Buffer.from(c);
      total += b.length;
      if (total > MAX_BYTES) return json(res, 413, { error: "음성 파일이 너무 큽니다. 4MB 이하 조각으로 나눠 보내 주세요" });
      chunks.push(b);
    }
    const audio = Buffer.concat(chunks);
    if (audio.length < 1000) return json(res, 400, { error: "음성 데이터가 비어 있습니다" });

    const contentType = (req.headers["content-type"] as string | undefined) ?? "audio/mp4";
    const hint = decodeHeader(req.headers["x-caller-hint"]);
    const result = await transcribe(audio, contentType, hint);
    console.log(JSON.stringify({ event: "transcribe", bytes: audio.length, ms: Date.now() - started, chars: result.text.length, model: result.model }));
    return json(res, 200, result);
  } catch (err) {
    const msg = err instanceof Error ? err.message : String(err);
    console.error("transcribe error:", msg);
    return json(res, 502, { error: `음성 인식 실패: ${msg}` });
  }
}

function decodeHeader(v: string | string[] | undefined): string | undefined {
  const s = Array.isArray(v) ? v[0] : v;
  if (!s) return undefined;
  try { return decodeURIComponent(s); } catch { return s; }
}
