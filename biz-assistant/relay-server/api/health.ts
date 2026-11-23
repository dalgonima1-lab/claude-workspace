import type { VercelRequest, VercelResponse } from "@vercel/node";
import { json, requireConfig } from "../lib/http.js";
import { modelId } from "../lib/claude.js";

export default function handler(_req: VercelRequest, res: VercelResponse): void {
  const missing = requireConfig();
  json(res, missing ? 503 : 200, { ok: !missing, model: modelId(), transcribe: Boolean(process.env.OPENAI_API_KEY), problem: missing ?? undefined });
}
