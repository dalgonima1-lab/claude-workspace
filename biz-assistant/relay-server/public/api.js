// 중계 서버 호출. 토큰은 이 브라우저에만 저장된다.
const KEY = "biz.token";
export const getToken = () => { try { return localStorage.getItem(KEY) || ""; } catch { return ""; } };
export const setToken = (t) => { try { if (t) localStorage.setItem(KEY, t); else localStorage.removeItem(KEY); } catch {} };

async function call(path, init = {}) {
  const res = await fetch(path, {
    ...init,
    headers: { "content-type": "application/json", "x-app-token": getToken(), ...(init.headers || {}) },
  });
  const text = await res.text();
  let body = {};
  try { body = text ? JSON.parse(text) : {}; } catch { body = { error: text }; }
  if (!res.ok) { const e = new Error(body.error || `서버 오류 (${res.status})`); e.status = res.status; throw e; }
  return body;
}

export const api = {
  health: () => call("/api/health"),
  stats: () => call("/api/data?stats=1"),
  pull: (since = 0) => call(`/api/data?since=${since}`),
  push: (items) => call("/api/data", { method: "POST", body: JSON.stringify({ items }) }),
  analyze: (req) => call("/api/analyze", { method: "POST", body: JSON.stringify(req) }),
  draftReply: (analysis) => call("/api/draft-reply", { method: "POST", body: JSON.stringify({ analysis, tone: "formal" }) }),
};

export const uid = () => (crypto.randomUUID ? crypto.randomUUID().replace(/-/g, "") : Math.random().toString(16).slice(2) + Date.now().toString(16));
