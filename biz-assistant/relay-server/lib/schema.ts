import { z } from "zod";

/** 내 소속. 공유 수신 시 기본값을 고르거나 AI가 추정한다. */
export const EntitySchema = z.enum(["제이피앤텍", "서일이앤씨", "불명"]);

/** 상대 조직 종류: 발주처 / 협력업체 / 자재·외주 / 기타 */
export const OrgKindSchema = z.enum(["client", "partner", "vendor", "other"]);

/** 사건 유형. notes/사업비서-앱-설계.md 2-2절과 같다. */
export const RecordTypeSchema = z.enum([
  "fault", // 장애·AS 접수
  "inspection", // 정기·수시 점검
  "delivery", // 납품·설치·시운전 일정
  "acceptance", // 검수·준공
  "document", // 서류 제출
  "quote", // 견적·입찰
  "contract", // 계약·변경
  "payment", // 대금·기성·세금계산서
  "material", // 자재·외주 발주
  "nightwork", // 야간 작업·출입 허가
  "meeting", // 회의·방문 약속
  "info", // 단순 정보 공유
  "personal", // 사업 무관
]);

export const SystemKindSchema = z.enum(["power_monitoring", "lighting_control", "both", "none"]);
export const UrgencySchema = z.enum(["high", "normal", "low"]);
export const OwnerSchema = z.enum(["me", "counterpart", "partner"]);
export const MoneyKindSchema = z.enum(["receivable", "payable", "quote", "contract"]);

export const EventSchema = z.object({
  title: z.string().describe("삼성 캘린더에 들어갈 제목. 현장과 목적이 드러나게"),
  start: z.string().describe("시작 시각. 'YYYY-MM-DDTHH:mm' (한국 시간). 시각을 모르면 09:00"),
  end: z.string().nullable().describe("종료 시각. 모르면 null (앱이 1시간으로 잡는다)"),
  place: z.string().nullable().describe("장소. 역명, 사업소, 본사 등"),
  all_day: z.boolean().describe("날짜만 있고 시각이 없으면 true"),
});

export const TaskSchema = z.object({
  title: z.string(),
  owner: OwnerSchema.describe("me=내가 할 일, counterpart=상대가 하기로 한 일, partner=협력업체(알트에이 등)가 할 일"),
  due: z.string().nullable().describe("기한 'YYYY-MM-DD'. 없으면 null"),
  note: z.string().nullable(),
});

export const MoneySchema = z.object({
  kind: MoneyKindSchema.describe("receivable=받을 돈, payable=줄 돈, quote=견적 금액, contract=계약 금액"),
  amount: z.number().int().describe("원 단위 정수"),
  due: z.string().nullable().describe("지급·청구 기한 'YYYY-MM-DD'"),
  note: z.string().nullable(),
});

export const CounterpartSchema = z.object({
  org: z.string().nullable().describe("상대 조직. 서울교통공사는 사업소·역·부서까지 (예: 서울교통공사 군자전기사업소)"),
  org_kind: OrgKindSchema,
  person: z.string().nullable().describe("담당자. 성과 직함 (예: 김 과장)"),
  phone: z.string().nullable(),
});

export const AnalysisSchema = z.object({
  is_business: z.boolean().describe("사업 관련이면 true. 가족·지인·광고 등은 false"),
  entity: EntitySchema,
  counterpart: CounterpartSchema,
  project: z.string().nullable().describe("사업·현장명. known_projects 에 있으면 그 이름을 그대로 쓴다"),
  system: SystemKindSchema,
  site: z.string().nullable().describe("현장. 역명 또는 사업소명"),
  type: RecordTypeSchema,
  urgency: UrgencySchema,
  summary: z.string().describe("한 줄 요약. 60자 이내"),
  decisions: z.array(z.string()).describe("합의·결정된 사항. 없으면 빈 배열"),
  events: z.array(EventSchema),
  tasks: z.array(TaskSchema),
  money: z.array(MoneySchema),
  reply_draft: z.string().nullable().describe("상대에게 보낼 확인 문자 초안. 존댓말, 3문장 이내. 사업 무관이면 null"),
  sensitive: z.boolean().describe("개인정보·분쟁·건강 등 민감한 내용이 섞여 있으면 true"),
  confidence: z.number().min(0).max(1).describe("분류와 추출 전체에 대한 확신도"),
});

export type Analysis = z.infer<typeof AnalysisSchema>;

/** 폰 앱이 보내는 요청 본문 */
export const AnalyzeRequestSchema = z.object({
  text: z.string().min(1).max(20000),
  source: z.enum(["call", "sms", "kakao", "mail", "visit", "memo"]).default("call"),
  received_at: z.string().datetime({ offset: true }).optional(),
  default_entity: EntitySchema.optional(),
  known_orgs: z.array(z.string()).max(200).default([]),
  known_projects: z.array(z.string()).max(200).default([]),
  caller_hint: z.string().max(200).optional(),
});
export type AnalyzeRequest = z.infer<typeof AnalyzeRequestSchema>;

export const DraftReplyRequestSchema = z.object({
  analysis: AnalysisSchema,
  tone: z.enum(["formal", "friendly"]).default("formal"),
});
export type DraftReplyRequest = z.infer<typeof DraftReplyRequestSchema>;
