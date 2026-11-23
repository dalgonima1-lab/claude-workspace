import { neon } from "@neondatabase/serverless";

/** 쿼리 함수 모양. 운영은 Neon(HTTP), 테스트는 내장 Postgres(pglite)를 같은 모양으로 넘긴다. */
export type Query = (text: string, params?: unknown[]) => Promise<Record<string, unknown>[]>;

export class DatabaseUnavailable extends Error {
  constructor() {
    super("DATABASE_URL 환경 변수가 없습니다. Vercel Storage에서 Neon 데이터베이스를 프로젝트에 연결하세요");
  }
}

let cached: Query | undefined;

export function getQuery(): Query {
  if (cached) return cached;
  const url = process.env.DATABASE_URL;
  if (!url) throw new DatabaseUnavailable();
  const sql = neon(url);
  cached = async (text, params) => (await sql.query(text, params ?? [])) as Record<string, unknown>[];
  return cached;
}
