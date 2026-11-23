// 코드값을 화면 이름으로. 서버 lib/schema.ts, 폰 Labels.kt 와 같은 짝.
export const ENTITIES = ["제이피앤텍", "서일이앤씨"];
export const TYPES = [
  ["fault", "장애 AS 접수"], ["inspection", "점검"], ["delivery", "납품 설치"], ["acceptance", "검수 준공"],
  ["document", "서류 제출"], ["quote", "견적 입찰"], ["contract", "계약"], ["payment", "대금 기성"],
  ["material", "자재 외주"], ["nightwork", "야간 작업"], ["meeting", "회의 방문"], ["info", "정보 공유"], ["personal", "사업 무관"],
];
export const URGENCIES = [["high", "높음"], ["normal", "보통"], ["low", "낮음"]];
export const ORG_KINDS = [["client", "발주처"], ["partner", "협력업체"], ["vendor", "자재 외주"], ["internal", "내부"], ["other", "기타"]];
export const SYSTEMS = [["power_monitoring", "전력감시"], ["lighting_control", "조명제어"], ["both", "전력감시와 조명제어"], ["none", "해당 없음"]];
export const OWNERS = [["me", "나"], ["counterpart", "상대"], ["partner", "협력업체"]];
export const MONEY_KINDS = [["receivable", "받을 돈"], ["payable", "줄 돈"], ["quote", "견적"], ["contract", "계약"]];
export const SOURCES = [["call", "통화"], ["sms", "문자"], ["kakao", "카톡"], ["mail", "메일"], ["visit", "방문"], ["memo", "메모"]];
export const TICKET_STATUSES = [["open", "접수"], ["scheduled", "방문 예정"], ["working", "조치 중"], ["done", "완료"]];

const name = (list) => (code) => (list.find(([c]) => c === code) || [code, code])[1];
export const typeName = name(TYPES);
export const urgencyName = name(URGENCIES);
export const orgKindName = name(ORG_KINDS);
export const systemName = name(SYSTEMS);
export const ownerName = name(OWNERS);
export const moneyKindName = name(MONEY_KINDS);
export const sourceName = name(SOURCES);
export const ticketStatusName = name(TICKET_STATUSES);

export const won = (n) => (n == null ? "금액 미정" : `${Number(n).toLocaleString("ko-KR")}원`);

const KST = "Asia/Seoul";
const dtf = new Intl.DateTimeFormat("ko-KR", { timeZone: KST, month: "numeric", day: "numeric", weekday: "short", hour: "2-digit", minute: "2-digit", hour12: false });
const df = new Intl.DateTimeFormat("ko-KR", { timeZone: KST, month: "numeric", day: "numeric", weekday: "short" });
const tf = new Intl.DateTimeFormat("ko-KR", { timeZone: KST, hour: "2-digit", minute: "2-digit", hour12: false });
const fullDf = new Intl.DateTimeFormat("ko-KR", { timeZone: KST, year: "numeric", month: "long", day: "numeric", weekday: "long" });

export const fmtDateTime = (ms) => (ms ? dtf.format(new Date(ms)) : "");
export const fmtTime = (ms) => (ms ? tf.format(new Date(ms)) : "");
export const fmtDate = (s) => { if (!s) return ""; const d = parseDate(s); return d ? df.format(d) : s; };
export const fmtToday = () => fullDf.format(new Date());

/** 'YYYY-MM-DD' 또는 'YYYY-MM-DDTHH:mm' → Date (한국 시간) */
export function parseDate(s) {
  if (!s) return null;
  const m = /^(\d{4})-(\d{2})-(\d{2})(?:T(\d{2}):(\d{2}))?/.exec(s);
  if (!m) return null;
  const iso = `${m[1]}-${m[2]}-${m[3]}T${m[4] || "09"}:${m[5] || "00"}:00+09:00`;
  const d = new Date(iso);
  return isNaN(d) ? null : d;
}
export const toMillis = (s) => { const d = parseDate(s); return d ? d.getTime() : null; };

export function todayKst() {
  const parts = new Intl.DateTimeFormat("en-CA", { timeZone: KST, year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date());
  return parts; // YYYY-MM-DD
}
export function addDays(ymd, n) {
  const d = new Date(`${ymd}T00:00:00+09:00`);
  d.setUTCDate(d.getUTCDate() + n);
  return new Intl.DateTimeFormat("en-CA", { timeZone: KST, year: "numeric", month: "2-digit", day: "2-digit" }).format(d);
}
export function startOfDayMs(ymd) { return new Date(`${ymd}T00:00:00+09:00`).getTime(); }

export function relative(ms) {
  if (!ms) return "";
  const days = Math.floor((startOfDayMs(todayKst()) - startOfDayMs(new Intl.DateTimeFormat("en-CA", { timeZone: KST, year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date(ms)))) / 86400000);
  if (days <= 0) return "오늘";
  if (days === 1) return "어제";
  if (days < 7) return `${days}일 전`;
  if (days < 30) return `${Math.floor(days / 7)}주 전`;
  return `${Math.floor(days / 30)}개월 전`;
}
export function dueLabel(due) {
  if (!due) return null;
  const diff = Math.round((startOfDayMs(due.slice(0, 10)) - startOfDayMs(todayKst())) / 86400000);
  if (diff === 0) return ["오늘까지", "red"];
  if (diff === 1) return ["내일까지", "red"];
  if (diff < 0) return [`${-diff}일 지남`, "red"];
  return [`${diff}일 남음`, diff <= 7 ? "amber" : ""];
}
/** "오늘 일정 {3건}" 꼴의 강조를 <b>로 */
export function voiceParts(text) {
  const out = []; let i = 0;
  while (i < text.length) {
    const o = text.indexOf("{", i); if (o < 0) { out.push(text.slice(i)); break; }
    out.push(text.slice(i, o));
    const c = text.indexOf("}", o); if (c < 0) { out.push(text.slice(o)); break; }
    out.push({ b: text.slice(o + 1, c) }); i = c + 1;
  }
  return out;
}
