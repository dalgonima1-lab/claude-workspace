import { timingSafeEqual } from "node:crypto";
import type { VercelRequest, VercelResponse } from "@vercel/node";
import Anthropic from "@anthropic-ai/sdk";
import { ZodError } from "zod";
import { RefusedError } from "./claude.js";

export function json(res: VercelResponse, status: number, body: unknown): void {
  res.status(status).setHeader("content-type", "application/json; charset=utf-8");
  res.send(JSON.stringify(body));
}

/** 폰 앱과 공유하는 비밀 토큰 검사. 길이가 달라도 시간 차이가 나지 않게 비교한다. */
export function isAuthorized(req: VercelRequest): boolean {
  const expected = process.env.APP_SHARED_TOKEN;
  if (!expected || expected.length < 16) return false;
  const header = req.headers["x-app-token"];
  const given = Array.isArray(header) ? header[0] : header;
  if (!given) return false;
  const a = Buffer.from(given);
  const b = Buffer.from(expected);
  if (a.length !== b.length) return false;
  return timingSafeEqual(a, b);
}

export function requireConfig(): string | null {
  if (!process.env.ANTHROPIC_API_KEY) return "ANTHROPIC_API_KEY 환경 변수가 없습니다";
  if (!process.env.APP_SHARED_TOKEN || process.env.APP_SHARED_TOKEN.length < 16) {
    return "APP_SHARED_TOKEN 환경 변수가 없거나 16자보다 짧습니다";
  }
  return null;
}

export function readBody(req: VercelRequest): unknown {
  const b = req.body;
  if (typeof b === "string") {
    try {
      return JSON.parse(b);
    } catch {
      return undefined;
    }
  }
  return b;
}

/** 오류를 상태 코드와 짧은 메시지로 바꾼다. 원문이나 키가 로그에 남지 않도록 메시지만 기록한다. */
export function sendError(res: VercelResponse, err: unknown): void {
  if (err instanceof ZodError) {
    return json(res, 400, { error: "요청 형식이 올바르지 않습니다", issues: err.issues.map((i) => `${i.path.join(".")}: ${i.message}`) });
  }
  if (err instanceof RefusedError) {
    return json(res, 422, { error: err.message, category: err.category });
  }
  if (err instanceof Anthropic.AuthenticationError) {
    console.error("claude auth error");
    return json(res, 500, { error: "Claude API 키가 올바르지 않습니다" });
  }
  if (err instanceof Anthropic.RateLimitError) {
    return json(res, 429, { error: "요청이 몰려 잠시 후 다시 시도해 주세요" });
  }
  if (err instanceof Anthropic.APIConnectionError) {
    console.error("claude connection error:", err.message);
    return json(res, 502, { error: "Claude API에 연결하지 못했습니다" });
  }
  if (err instanceof Anthropic.APIError) {
    console.error("claude api error", err.status, err.message);
    return json(res, 502, { error: `Claude API 오류 (${err.status})` });
  }
  console.error("unexpected error:", err instanceof Error ? err.message : String(err));
  return json(res, 500, { error: "서버 오류" });
}
