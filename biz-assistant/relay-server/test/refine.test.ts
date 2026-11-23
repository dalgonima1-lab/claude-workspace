import { test } from "node:test";
import assert from "node:assert/strict";
import { RefineRequestSchema, RefineResultSchema } from "../lib/refine.js";

test("보완 요청 스키마: 답변 하나 이상, 기존 항목은 uid 포함", () => {
  const r = RefineRequestSchema.parse({
    record: { entity: "제이피앤텍", org: "제이피앤텍", org_kind: "internal", person: "회장님", project: null, site: "개화산역", system: "power_monitoring", type: "delivery", urgency: "high", summary: "전기실 납품", decisions: [], questions: ["잔금 금액은?"] },
    raw_text: "...",
    answers: [{ question: "잔금 금액은?", answer: "350만원" }],
    money: [{ uid: "m1", kind: "receivable", amount: null, due: null, note: "잔금" }],
  });
  assert.equal(r.events.length, 0);
  assert.equal(r.money[0].uid, "m1");
  assert.throws(() => RefineRequestSchema.parse({ ...r, answers: [] }));
});

test("보완 결과 스키마: 모델 응답 예시가 통과", () => {
  const out = RefineResultSchema.parse({
    note_for_user: "잔금 350만원으로 적어 두었습니다.", summary: "개화산역 전기실 납품, 잔금 350만원", decisions: ["잔금 3,500,000원"], remaining_questions: [],
    org: "제이피앤텍", org_kind: "internal", person: "회장님", project: null, site: "개화산역", system: "power_monitoring", type: "delivery", urgency: "high",
    event_updates: [], task_updates: [], money_updates: [{ uid: "m1", amount: 3500000, due: null, note: null }], new_events: [], new_tasks: [], new_money: [],
  });
  assert.equal(out.money_updates[0].amount, 3500000);
});
