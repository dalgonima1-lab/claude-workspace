import { test } from "node:test";
import assert from "node:assert/strict";
import { isAuthorized, requireConfig } from "../lib/http.js";

function fakeReq(token?: string) {
  return { headers: token === undefined ? {} : { "x-app-token": token } } as unknown as import("@vercel/node").VercelRequest;
}

test("인증: 같은 토큰만 통과, 짧은 토큰 설정은 전부 거부", () => {
  process.env.APP_SHARED_TOKEN = "0123456789abcdef0123456789abcdef";
  assert.equal(isAuthorized(fakeReq("0123456789abcdef0123456789abcdef")), true);
  assert.equal(isAuthorized(fakeReq("0123456789abcdef0123456789abcdeX")), false);
  assert.equal(isAuthorized(fakeReq("short")), false);
  assert.equal(isAuthorized(fakeReq()), false);
  process.env.APP_SHARED_TOKEN = "short";
  assert.equal(isAuthorized(fakeReq("short")), false);
});

test("설정 검사: 키와 토큰이 없으면 이유를 알려준다", () => {
  const savedKey = process.env.ANTHROPIC_API_KEY;
  delete process.env.ANTHROPIC_API_KEY;
  process.env.APP_SHARED_TOKEN = "0123456789abcdef0123456789abcdef";
  assert.match(requireConfig() ?? "", /ANTHROPIC_API_KEY/);
  process.env.ANTHROPIC_API_KEY = "sk-ant-test";
  assert.equal(requireConfig(), null);
  process.env.APP_SHARED_TOKEN = "short";
  assert.match(requireConfig() ?? "", /APP_SHARED_TOKEN/);
  if (savedKey) process.env.ANTHROPIC_API_KEY = savedKey;
  else delete process.env.ANTHROPIC_API_KEY;
});
