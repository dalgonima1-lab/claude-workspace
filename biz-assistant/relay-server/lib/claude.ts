import Anthropic from "@anthropic-ai/sdk";
import { betaZodOutputFormat } from "@anthropic-ai/sdk/helpers/beta/zod";
import { AnalysisSchema, type Analysis, type AnalyzeRequest, type DraftReplyRequest } from "./schema.js";
import { SYSTEM_PROMPT, buildUserMessage } from "./prompt.js";

const EFFORTS = ["low", "medium", "high", "xhigh", "max"] as const;
type Effort = (typeof EFFORTS)[number];

function effort(): Effort {
  const e = process.env.CLAUDE_EFFORT;
  return (EFFORTS as readonly string[]).includes(e ?? "") ? (e as Effort) : "high";
}

export function modelId(): string {
  return process.env.CLAUDE_MODEL?.trim() || "claude-opus-5-5";
}

let cached: Anthropic | undefined;
export function client(): Anthropic {
  // ANTHROPIC_API_KEY 환경 변수에서 키를 읽는다. 코드에 키를 넣지 않는다.
  return (cached ??= new Anthropic({ maxRetries: 2, timeout: 60_000 }));
}

export class RefusedError extends Error {
  constructor(public category: string | null) {
    super("분석 요청이 안전 정책으로 거부되었습니다");
  }
}

export interface AnalyzeResult {
  analysis: Analysis;
  model: string;
  usage: { input_tokens: number; output_tokens: number; cache_read_input_tokens: number | null };
}

export async function analyze(req: AnalyzeRequest): Promise<AnalyzeResult> {
  const response = await client().beta.messages.parse({
    model: modelId(),
    max_tokens: 16000,
    // Claude Opus 5.5는 thinking이 항상 켜져 있으므로 thinking 파라미터는 생략하고 effort로만 조절한다.
    output_config: { effort: effort(), format: betaZodOutputFormat(AnalysisSchema) },
    // 안전 분류기가 요청을 거부하면 서버가 대체 모델로 같은 요청을 다시 돌린다.
    betas: ["server-side-fallback-2026-07-01"],
    fallbacks: "default",
    system: [{ type: "text", text: SYSTEM_PROMPT, cache_control: { type: "ephemeral" } }],
    messages: [{ role: "user", content: buildUserMessage(req) }],
  });

  if (response.stop_reason === "refusal") {
    throw new RefusedError(response.stop_details?.category ?? null);
  }
  if (response.stop_reason === "max_tokens") {
    throw new Error("응답이 잘렸습니다 (max_tokens)");
  }
  const parsed = response.parsed_output;
  if (!parsed) {
    throw new Error("구조화 출력 파싱에 실패했습니다");
  }
  return {
    analysis: parsed,
    model: response.model,
    usage: {
      input_tokens: response.usage.input_tokens,
      output_tokens: response.usage.output_tokens,
      cache_read_input_tokens: response.usage.cache_read_input_tokens ?? null,
    },
  };
}

const REPLY_SYSTEM = `당신은 제이피앤텍(또는 서일이앤씨) 대표가 거래처에 보낼 확인 문자를 쓴다.
- 존댓말, 3~4문장, 120자 이내. 인사 한 줄, 확인 내용(날짜·시각·장소·할 일), 마무리 한 줄.
- 상대 호칭은 "김 과장님"처럼 담당자명+직함+님.
- 서울교통공사 담당자에게는 격식체, 협력업체에게는 간결한 업무체.
- 확정되지 않은 내용을 확정처럼 쓰지 않는다. 문자 본문만 출력한다.`;

export async function draftReply(req: DraftReplyRequest): Promise<string> {
  const a = req.analysis;
  const facts = [
    `소속: ${a.entity}`,
    `상대: ${a.counterpart.org ?? "미상"} ${a.counterpart.person ?? ""} (${a.counterpart.org_kind})`,
    `사업: ${a.project ?? "미상"}`,
    `유형: ${a.type} / 긴급도 ${a.urgency}`,
    `요약: ${a.summary}`,
    `결정: ${a.decisions.join("; ") || "없음"}`,
    `일정: ${a.events.map((e) => `${e.start} ${e.title}${e.place ? ` @${e.place}` : ""}`).join("; ") || "없음"}`,
    `할 일: ${a.tasks.map((t) => `[${t.owner}] ${t.title}${t.due ? ` (${t.due})` : ""}`).join("; ") || "없음"}`,
    `금전: ${a.money.map((m) => `${m.kind} ${m.amount}원${m.due ? ` (${m.due})` : ""}`).join("; ") || "없음"}`,
    `말투: ${req.tone === "friendly" ? "부드럽게" : "격식"}`,
  ].join("\n");

  const response = await client().beta.messages.create({
    model: modelId(),
    max_tokens: 2000,
    output_config: { effort: "low" },
    betas: ["server-side-fallback-2026-07-01"],
    fallbacks: "default",
    system: REPLY_SYSTEM,
    messages: [{ role: "user", content: facts }],
  });
  if (response.stop_reason === "refusal") throw new RefusedError(response.stop_details?.category ?? null);
  const text = response.content
    .filter((b): b is Extract<typeof b, { type: "text" }> => b.type === "text")
    .map((b) => b.text)
    .join("")
    .trim();
  if (!text) throw new Error("답장 초안이 비어 있습니다");
  return text;
}
