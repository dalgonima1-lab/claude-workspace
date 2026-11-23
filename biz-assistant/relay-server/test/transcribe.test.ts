import { test } from "node:test";
import assert from "node:assert/strict";
import { looksLikePromptEcho } from "../lib/transcribe.js";

const PROMPT = "서울교통공사 전기사업소 전력감시시스템 조명제어시스템 전기실 납품 검수 잔금 알트에이 개화산역";

test("힌트 문구를 그대로 받아 적은 결과는 빈 것으로 본다", () => {
  assert.equal(looksLikePromptEcho("서울교통공사 전기사업소 전력감시시스템 조명제어시스템 전기실 납품 검수", PROMPT), true);
  assert.equal(looksLikePromptEcho("", PROMPT), true);
});

test("실제 대화는 힌트 단어가 섞여 있어도 통과한다", () => {
  assert.equal(looksLikePromptEcho("개화산역 전기실 납품은 내일 오전에 하고요, 검수는 바로 받을 수 있을까요?", PROMPT), false);
  assert.equal(looksLikePromptEcho("네 안녕하세요 차장님, 지난주에 보낸 물품 잘 받으셨죠?", PROMPT), false);
});
