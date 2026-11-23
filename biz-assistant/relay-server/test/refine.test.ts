import { test } from "node:test";
import assert from "node:assert/strict";
import { RefineRequestSchema, RefineResultSchema, toResult } from "../lib/refine.js";

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

test("모델의 단순 응답을 클라이언트 모양으로 바꾼다", () => {
  const req = RefineRequestSchema.parse({
    record: { entity: "제이피앤텍", org: "제이피앤텍", org_kind: "internal", person: "회장님", project: null, site: null, system: "power_monitoring", type: "delivery", urgency: "high", summary: "s", decisions: [], questions: ["q"] },
    raw_text: "...", answers: [{ question: "q", answer: "a" }],
    events: [{ uid: "e1", title: "납품", start: "2026-11-24T09:00", end: null, place: null }],
    money: [{ uid: "m1", kind: "receivable", amount: null, due: null, note: null }],
  });
  const out = toResult({
    note_for_user: "반영했습니다.", summary: "새 요약", decisions: ["잔금 350만원"], remaining_questions: [], org: null, person: null, project: "4개 전기실", site: "당곡역", type: "bogus", urgency: "low", system: null,
    updates: [{ kind: "money", uid: "m1", field: "amount", value: "3,500,000원" }, { kind: "event", uid: "e1", field: "start", value: "2026-11-24T10:00" }, { kind: "task", uid: "없음", field: "done", value: "true" }],
    new_events: [], new_tasks: [{ title: "검사 준비", owner: "partner", due: null, note: null }], new_money: [],
  }, req);
  assert.equal(out.money_updates[0].amount, 3500000);
  assert.equal(out.event_updates[0].start, "2026-11-24T10:00");
  assert.equal(out.task_updates.length, 0);
  assert.equal(out.type, "delivery"); // 모르는 코드는 기존 값 유지
  assert.equal(out.urgency, "low");
  assert.equal(out.system, "power_monitoring");
  assert.equal(out.org, "제이피앤텍");
  assert.equal(out.project, "4개 전기실");
  assert.equal(out.new_tasks[0].owner, "partner");
  assert.doesNotThrow(() => RefineResultSchema.parse(out));
});
