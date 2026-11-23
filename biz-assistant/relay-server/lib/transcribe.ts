/**
 * OpenAI 음성 인식. Claude API는 음성을 받지 않으므로 이 단계만 OpenAI를 쓴다.
 * 모델은 환경 변수 OPENAI_TRANSCRIBE_MODEL 로 바꿀 수 있다 (기본 gpt-4o-mini-transcribe).
 */
const DOMAIN_PROMPT =
  "서울교통공사 전기사업소 전력감시시스템 조명제어시스템 전기실 역사 납품 검수 준공 기성 잔금 선금 세금계산서 시험성적서 " +
  "제이피앤텍 서일이앤씨 알트에이 개화산역 어린이대공원역 군자역 신답역 용답역 을지로 야간작업 출입허가 제어기 RTU 경보";

export interface TranscribeResult {
  text: string;
  model: string;
}

export async function transcribe(audio: Buffer, contentType: string, callerHint?: string): Promise<TranscribeResult> {
  const key = process.env.OPENAI_API_KEY;
  if (!key) throw new Error("OPENAI_API_KEY 없음");
  const model = process.env.OPENAI_TRANSCRIBE_MODEL?.trim() || "gpt-4o-mini-transcribe";
  const ext = extensionFor(contentType);

  const form = new FormData();
  form.append("file", new Blob([new Uint8Array(audio)], { type: contentType }), `call.${ext}`);
  form.append("model", model);
  form.append("language", "ko");
  form.append("response_format", "json");
  form.append("prompt", callerHint ? `${callerHint}와의 통화. ${DOMAIN_PROMPT}` : DOMAIN_PROMPT);

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 110_000);
  try {
    const res = await fetch("https://api.openai.com/v1/audio/transcriptions", {
      method: "POST",
      headers: { authorization: `Bearer ${key}` },
      body: form,
      signal: controller.signal,
    });
    const body = (await res.json().catch(() => ({}))) as { text?: string; error?: { message?: string } };
    if (!res.ok) throw new Error(body.error?.message ?? `OpenAI ${res.status}`);
    return { text: (body.text ?? "").trim(), model };
  } finally {
    clearTimeout(timer);
  }
}

function extensionFor(contentType: string): string {
  const t = contentType.toLowerCase();
  if (t.includes("mp4") || t.includes("m4a") || t.includes("aac")) return "m4a";
  if (t.includes("mpeg") || t.includes("mp3")) return "mp3";
  if (t.includes("wav")) return "wav";
  if (t.includes("ogg") || t.includes("opus")) return "ogg";
  if (t.includes("webm")) return "webm";
  if (t.includes("amr")) return "amr";
  if (t.includes("3gp")) return "3gp";
  return "m4a";
}
