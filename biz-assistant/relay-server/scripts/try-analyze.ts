/**
 * 로컬에서 실제 Claude API를 한 번 호출해 본다.
 * 사용법:  ANTHROPIC_API_KEY=... npm run try            (내장 샘플)
 *          ANTHROPIC_API_KEY=... npm run try -- 파일.txt  (파일 내용으로)
 */
import { readFileSync } from "node:fs";
import { AnalyzeRequestSchema } from "../lib/schema.js";
import { analyze, draftReply } from "../lib/claude.js";
import { SAMPLE_CALL } from "../test/fixtures.js";

const file = process.argv[2];
const text = file ? readFileSync(file, "utf8") : SAMPLE_CALL;

const req = AnalyzeRequestSchema.parse({
  text,
  source: "call",
  default_entity: "제이피앤텍",
  known_orgs: ["서울교통공사 군자전기사업소", "서울교통공사 신답역 역무실", "알트에이"],
  known_projects: ["군자역 조명제어 교체", "군자역 전력감시 유지보수"],
});

const result = await analyze(req);
console.log(JSON.stringify(result, null, 2));
if (result.analysis.is_business) {
  console.log("\n--- 답장 초안 ---");
  console.log(await draftReply({ analysis: result.analysis, tone: "formal" }));
}
