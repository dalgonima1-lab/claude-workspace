# 사업 비서 중계 서버

폰 앱(갤럭시 폴드 8)과 Claude API 사이에 두는 작은 서버다. Vercel 서버리스 함수로 배포한다.
Claude API 키는 이 서버의 환경 변수에만 두고, 폰 앱은 공유 토큰으로 이 서버만 호출한다.

## 엔드포인트

| 경로 | 방식 | 역할 |
|---|---|---|
| `/api/health` | GET | 설정 상태 확인 (키·토큰 유무, 사용 모델) |
| `/api/analyze` | POST | 통화 요약·문자·카톡 텍스트 → 사업 기록 JSON |
| `/api/draft-reply` | POST | 분석 결과 → 거래처에 보낼 확인 문자 초안 |
| `/api/transcribe` | POST | 통화 녹음 조각(4MB 이하, 본문에 파일 바이트) → 글자. OpenAI 음성 인식 사용 |

모든 POST 요청에 `x-app-token: <APP_SHARED_TOKEN>` 헤더가 필요하다.

### `/api/analyze` 요청

```json
{
  "text": "[통화 요약] ...",
  "source": "call",
  "received_at": "2026-11-23T14:20:00+09:00",
  "default_entity": "제이피앤텍",
  "known_orgs": ["서울교통공사 군자전기사업소", "알트에이"],
  "known_projects": ["군자역 조명제어 교체"],
  "caller_hint": "김 과장 (군자사업소)"
}
```

`source`: `call | sms | kakao | mail | visit | memo`. 통화 입력은 갤럭시 AI 요약문과 화자 표시 없는 전사본 둘 다 받는다. `known_*`는 폰에 저장된 거래처·사업 이름 목록으로, 이름 매칭 정확도를 높인다.

### `/api/analyze` 응답

```json
{
  "analysis": { "is_business": true, "entity": "제이피앤텍", "counterpart": {...}, "project": "...", "type": "fault", "urgency": "high", "summary": "...", "decisions": [], "events": [], "tasks": [], "money": [], "questions": ["확인할 점"], "reply_draft": "...", "sensitive": false, "confidence": 0.9 },
  "model": "claude-opus-5-5",
  "usage": { "input_tokens": 0, "output_tokens": 0, "cache_read_input_tokens": 0 }
}
```

필드 정의는 `lib/schema.ts`, 분류 규칙은 `lib/prompt.ts`에 있다. 설계 배경은 `notes/사업비서-앱-설계.md`.

오류 코드: 400 요청 형식 오류, 401 토큰 불일치, 422 안전 정책 거부, 429 사용량 제한, 502 Claude API 오류, 503 서버 설정 누락.

## 환경 변수

`.env.example` 참고. `ANTHROPIC_API_KEY`, `APP_SHARED_TOKEN`은 필수. `OPENAI_API_KEY`는 통화 녹음 자동 처리에만 필요. 토큰은 `openssl rand -hex 32`로 만든다.

## 로컬 확인

```bash
npm install
npm run typecheck
npm test
# 실제 Claude 호출 (키 필요)
ANTHROPIC_API_KEY=sk-ant-... npm run try
```

## Vercel 배포

1. Vercel 프로젝트를 만들고 이 폴더(`biz-assistant/relay-server`)를 루트로 지정한다.
2. 프로젝트 설정 → Environment Variables 에 `ANTHROPIC_API_KEY`, `APP_SHARED_TOKEN` 을 넣는다. (Production, Preview 모두)
3. 배포 후 `https://<프로젝트>.vercel.app/api/health` 가 `{"ok":true}` 를 돌려주면 끝.
4. 폰 앱 설정에 서버 주소와 `APP_SHARED_TOKEN` 을 넣는다.

## 설계 메모

- 모델은 `claude-opus-5-5` 기본, `CLAUDE_MODEL=claude-haiku-5-5` 로 바꾸면 비용이 크게 줄어든다.
- 구조화 출력으로 응답 JSON 형식을 강제한다. 파싱 실패나 잘림은 500으로 돌려준다.
- 안전 분류기 거부 시 서버 측 대체 모델로 자동 재시도(`fallbacks: "default"`)한다.
- 시스템 프롬프트는 고정이라 프롬프트 캐시에 올라가고, 요청마다 바뀌는 내용은 사용자 메시지에만 둔다.
- 로그에는 원문을 남기지 않는다. 글자 수, 소요 시간, 토큰 수만 기록한다.
