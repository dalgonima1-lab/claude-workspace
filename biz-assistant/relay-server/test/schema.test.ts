import { test } from "node:test";
import assert from "node:assert/strict";
import { AnalysisSchema, AnalyzeRequestSchema } from "../lib/schema.js";
import { buildUserMessage } from "../lib/prompt.js";
import { SAMPLE_CALL } from "./fixtures.js";

test("요청 스키마: 기본값이 채워진다", () => {
  const r = AnalyzeRequestSchema.parse({ text: SAMPLE_CALL });
  assert.equal(r.source, "call");
  assert.deepEqual(r.known_orgs, []);
  assert.deepEqual(r.known_projects, []);
});

test("요청 스키마: 빈 텍스트와 잘못된 source는 거부", () => {
  assert.throws(() => AnalyzeRequestSchema.parse({ text: "" }));
  assert.throws(() => AnalyzeRequestSchema.parse({ text: "x", source: "fax" }));
});

test("사용자 메시지: 기준 시각이 한국 시간으로, 알려진 거래처가 포함된다", () => {
  const r = AnalyzeRequestSchema.parse({
    text: SAMPLE_CALL,
    received_at: "2026-11-23T05:20:00Z",
    default_entity: "제이피앤텍",
    known_orgs: ["서울교통공사 군자전기사업소", "알트에이"],
    known_projects: ["군자역 조명제어 교체"],
  });
  const msg = buildUserMessage(r);
  assert.match(msg, /2026\. 11\. 23\. 14:20/);
  assert.match(msg, /월요일/);
  assert.match(msg, /default_entity: 제이피앤텍/);
  assert.match(msg, /known_orgs: 서울교통공사 군자전기사업소 \| 알트에이/);
  assert.match(msg, /<입력>\n\[통화 요약\]/);
});

test("분석 스키마: 모델이 돌려줄 형태의 예시가 통과한다", () => {
  const sample = {
    is_business: true,
    entity: "제이피앤텍",
    counterpart: { org: "서울교통공사 군자전기사업소", org_kind: "client", person: "김 과장", phone: null },
    project: "군자역 조명제어 교체",
    system: "lighting_control",
    site: "군자역",
    type: "fault",
    urgency: "high",
    summary: "군자역 B2 조명 제어기 3번 그룹 응답 없음, 내일 오전 방문 요청",
    decisions: ["11/24 오전 방문 점검", "교체 대비 제어기 1대 지참"],
    events: [{ title: "군자역 B2 조명 제어기 점검", start: "2026-11-24T10:00", end: null, place: "군자역 B2 승강장", all_day: false }],
    tasks: [
      { title: "교체용 제어기 1대 준비", owner: "partner", due: "2026-11-24", note: "알트에이 이 팀장" },
      { title: "조치 결과 보고서 제출", owner: "me", due: null, note: null },
    ],
    money: [],
    reply_draft: "김 과장님, 내일 오전 10시에 군자역 B2 방문하여 점검하겠습니다.",
    sensitive: false,
    confidence: 0.9,
  };
  const parsed = AnalysisSchema.parse(sample);
  assert.equal(parsed.type, "fault");
  assert.equal(parsed.tasks[0].owner, "partner");
});

test("분석 스키마: 잘못된 유형과 범위 밖 확신도는 거부", () => {
  assert.throws(() => AnalysisSchema.parse({ is_business: true, type: "unknown" }));
});
