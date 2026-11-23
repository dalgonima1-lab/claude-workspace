import { h, render } from "preact";
import { useState, useEffect, useMemo, useRef } from "preact/hooks";
import htm from "htm";
import { api, getToken, setToken, uid } from "./api.js";
import * as L from "./labels.js";

const html = htm.bind(h);

/* ---------- 저장소: 서버의 items 를 종류별로 들고 있는다 ---------- */
function useStore() {
  const [items, setItems] = useState({}); // kind -> { uid -> {uid, updatedAt, data} }
  const [cursor, setCursor] = useState(0);
  const [status, setStatus] = useState("loading"); // loading | ready | error
  const [error, setError] = useState(null);
  const cursorRef = useRef(0);

  const apply = (list) => {
    setItems((prev) => {
      const next = { ...prev };
      for (const it of list) {
        const bucket = { ...(next[it.kind] || {}) };
        if (it.deleted) delete bucket[it.uid]; else bucket[it.uid] = { uid: it.uid, updatedAt: it.updatedAt, ...it.data };
        next[it.kind] = bucket;
      }
      return next;
    });
  };

  const pull = async () => {
    try {
      const r = await api.pull(cursorRef.current);
      apply(r.items);
      cursorRef.current = r.cursor; setCursor(r.cursor);
      setStatus("ready"); setError(null);
    } catch (e) { setStatus("error"); setError(e); }
  };

  const save = async (list) => {
    const now = Date.now();
    const payload = list.map((x) => ({ kind: x.kind, uid: x.uid, updatedAt: now, deleted: !!x.deleted, data: x.data || {} }));
    apply(payload);
    await api.push(payload);
  };

  useEffect(() => { pull(); const t = setInterval(pull, 45_000); return () => clearInterval(t); }, []);

  const live = (kind) => Object.values(items[kind] || {});
  return { items, live, save, pull, status, error, cursor };
}

/* ---------- 작은 조각들 ---------- */
const Voice = ({ text }) => html`<span>${L.voiceParts(text).map((p) => (typeof p === "string" ? p : html`<b>${p.b}</b>`))}</span>`;
const Band = ({ kicker, text }) => html`
  <div class="band"><div class="ring"></div>
    <div>${kicker && html`<div class="kicker">${kicker}</div>`}<div class="voice"><${Voice} text=${text} /></div></div>
  </div>`;
const Pill = ({ tone = "", children }) => html`<span class=${"pill " + tone}>${children}</span>`;
const TypePill = ({ type }) => html`<${Pill} tone=${type === "fault" ? "red" : type === "payment" ? "green" : ["document", "nightwork"].includes(type) ? "amber" : ""}>${L.typeName(type)}</${Pill}>`;
const EntityPill = ({ entity }) => html`<${Pill} tone=${entity === "서일이앤씨" ? "teal" : "signal"}>${entity}</${Pill}>`;
const Chips = ({ options, value, onChange }) => html`
  <div class="chips">${options.map(([code, name]) => html`<button type="button" class=${"chip" + (value === code ? " on" : "")} onClick=${() => onChange(code)}>${name}</button>`)}</div>`;
const Select = ({ label, options, value, onChange }) => html`
  <label class="f">${label}<select value=${value} onChange=${(e) => onChange(e.target.value)}>${options.map(([c, n]) => html`<option value=${c}>${n}</option>`)}</select></label>`;
const Field = ({ label, value, onChange, type = "text", placeholder = "" }) => html`
  <label class="f">${label}<input type=${type} value=${value ?? ""} placeholder=${placeholder} onInput=${(e) => onChange(e.target.value)} /></label>`;

/* ---------- 로그인 ---------- */
function Login({ onDone }) {
  const [token, setTok] = useState("");
  const [msg, setMsg] = useState(null);
  const go = async () => {
    setMsg("확인 중");
    setToken(token.trim());
    try { await api.stats(); onDone(); } catch (e) { setToken(""); setMsg(e.status === 401 ? "토큰이 맞지 않습니다" : e.message); }
  };
  return html`<div class="login">
    <${Band} text="안녕하세요. 사업 비서입니다. 폰 앱 설정에 넣은 {서버 토큰}을 한 번 입력하면 이 컴퓨터에서도 같은 기록을 봅니다." />
    <div class="panel">
      <label class="f">서버 토큰<input type="password" value=${token} onInput=${(e) => setTok(e.target.value)} onKeyDown=${(e) => e.key === "Enter" && go()} /></label>
      <div class="actions"><button class="btn primary" onClick=${go} disabled=${!token.trim()}>들어가기</button>${msg && html`<span class="sub">${msg}</span>`}</div>
    </div>
    <div class="sub">공용 컴퓨터라면 다 쓰고 설정에서 로그아웃하세요.</div>
  </div>`;
}

/* ---------- 확인 카드 (붙여넣어 정리, 대기함 확인) ---------- */
const STEPS = ["내용 읽기", "누구와의 대화인지 확인", "일정과 약속 찾기", "금액과 할 일 정리", "거래처 기록과 대조"];

function draftFrom(a, rawText, source, defaultEntity) {
  return {
    rawText, source,
    entity: a.entity === "불명" ? defaultEntity : a.entity,
    orgName: a.counterpart?.org || "", orgKind: a.counterpart?.org_kind || "other", person: a.counterpart?.person || "",
    projectName: a.project || "", system: a.system || "none", site: a.site || "", type: a.type || "info", urgency: a.urgency || "normal",
    summary: a.summary || "", decisions: a.decisions || [], questions: a.questions || [],
    events: (a.events || []).map((e) => ({ ...e, include: true })),
    tasks: (a.tasks || []).map((t) => ({ ...t, include: true })),
    money: (a.money || []).map((m) => ({ ...m, include: true })),
    replyDraft: a.reply_draft || null, sensitive: !!a.sensitive, confidence: a.confidence ?? 0,
    createTicket: a.type === "fault",
  };
}
function toAnalysis(d) {
  return {
    is_business: d.type !== "personal", entity: d.entity,
    counterpart: { org: d.orgName || null, org_kind: d.orgKind, person: d.person || null, phone: null },
    project: d.projectName || null, system: d.system, site: d.site || null, type: d.type, urgency: d.urgency, summary: d.summary,
    decisions: d.decisions, events: d.events.filter((e) => e.include).map(({ include, ...e }) => e),
    tasks: d.tasks.filter((t) => t.include).map(({ include, ...t }) => t), money: d.money.filter((m) => m.include).map(({ include, ...m }) => m),
    reply_draft: d.replyDraft, sensitive: d.sensitive, confidence: d.confidence, questions: d.questions,
  };
}
function readySentence(d) {
  if (d.type === "personal") return "사업과 관계없는 대화로 보입니다. 저장하지 않으려면 아래에서 버리면 됩니다.";
  const parts = [];
  if (d.events.length) parts.push(`일정 {${d.events.length}건}`);
  if (d.tasks.length) parts.push(`할 일 {${d.tasks.length}건}`);
  if (d.money.length) parts.push(`금전 {${d.money.length}건}`);
  const found = parts.length ? parts.join(", ") + "을 찾았습니다." : "따로 등록할 일정이나 할 일은 찾지 못했습니다.";
  const q = d.questions.length ? ` 확인할 점이 {${d.questions.length}가지} 있습니다.` : "";
  return `{${L.typeName(d.type)}}${d.urgency === "high" ? ", 긴급으로 보입니다." : "으로 정리했습니다."} ${found}${q}`;
}

/** 확인 카드 내용을 서버 항목으로 바꾼다 (폰 Repository.save 와 같은 규칙) */
function buildItems(d, store, pendingUid) {
  const now = Date.now();
  const out = [];
  let orgUid = null;
  if (d.orgName.trim()) {
    const existing = store.live("org").find((o) => o.name === d.orgName.trim());
    orgUid = existing ? existing.uid : uid();
    out.push({ kind: "org", uid: orgUid, data: { name: d.orgName.trim(), kind: d.orgKind, lastContactAt: now, lastPerson: d.person || existing?.lastPerson || null } });
  }
  let projectUid = null;
  if (d.projectName.trim()) {
    const existing = store.live("project").find((p) => p.name === d.projectName.trim());
    projectUid = existing ? existing.uid : uid();
    if (!existing) out.push({ kind: "project", uid: projectUid, data: { name: d.projectName.trim(), entity: d.entity, orgUid, system: d.system, status: "active", warrantyEnd: null } });
  }
  const recordUid = uid();
  out.push({ kind: "record", uid: recordUid, data: {
    createdAt: now, source: d.source, entity: d.entity, orgUid, orgName: d.orgName || null, person: d.person || null, projectUid, projectName: d.projectName || null,
    site: d.site || null, system: d.system, type: d.type, urgency: d.urgency, summary: d.summary, decisions: d.decisions, questions: d.questions,
    rawText: d.rawText, replyDraft: d.replyDraft, sensitive: d.sensitive, confidence: d.confidence,
  } });
  for (const e of d.events.filter((x) => x.include)) {
    const start = L.toMillis(e.start); if (!start) continue;
    const end = L.toMillis(e.end) || start + 3600000;
    out.push({ kind: "event", uid: uid(), data: { recordUid, title: e.title, startMillis: start, endMillis: end, place: e.place || null, calendarEventId: null } });
  }
  for (const t of d.tasks.filter((x) => x.include)) out.push({ kind: "task", uid: uid(), data: { recordUid, title: t.title, owner: t.owner || "me", due: t.due || null, note: t.note || null, done: false, reminderAt: null } });
  for (const m of d.money.filter((x) => x.include)) out.push({ kind: "money", uid: uid(), data: { recordUid, kind: m.kind, amount: m.amount ?? null, due: m.due || null, note: m.note || null, settled: false } });
  if (d.createTicket) out.push({ kind: "ticket", uid: uid(), data: { recordUid, orgUid, orgName: d.orgName || null, entity: d.entity, site: d.site || null, system: d.system, symptom: d.summary, urgency: d.urgency, status: d.events.some((x) => x.include) ? "scheduled" : "open", createdAt: now, closedAt: null } });
  if (pendingUid) out.push({ kind: "pending", uid: pendingUid, deleted: true, data: {} });
  return out;
}

function Confirm({ store, initial, onDone, toast }) {
  // initial: { text, source, pending? }
  const [phase, setPhase] = useState(initial.analysis ? "ready" : "analyzing");
  const [step, setStep] = useState(0);
  const [d, setD] = useState(initial.analysis ? draftFrom(initial.analysis, initial.text, initial.source, "제이피앤텍") : null);
  const [err, setErr] = useState(null);
  const [reply, setReply] = useState(null);
  const [showRaw, setShowRaw] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (initial.analysis) return;
    let alive = true;
    const timer = setInterval(() => setStep((s) => Math.min(s + 1, STEPS.length - 1)), 2600);
    (async () => {
      try {
        const r = await api.analyze({
          text: initial.text, source: initial.source, received_at: new Date().toISOString(), default_entity: "제이피앤텍",
          known_orgs: store.live("org").map((o) => o.name), known_projects: store.live("project").map((p) => p.name),
        });
        if (!alive) return;
        setD(draftFrom(r.analysis, initial.text, initial.source, "제이피앤텍")); setPhase("ready");
      } catch (e) { if (alive) { setErr(e.message); setPhase("failed"); } }
    })();
    return () => { alive = false; clearInterval(timer); };
  }, []);

  const set = (patch) => setD((x) => ({ ...x, ...patch }));
  const toggle = (key, i) => setD((x) => ({ ...x, [key]: x[key].map((v, j) => (j === i ? { ...v, include: !v.include } : v)) }));

  if (phase === "analyzing") return html`<div>
    <${Band} kicker="분석 중" text=${["내용을 읽고 있습니다.", "누구와의 대화인지 확인하고 있습니다.", "날짜와 약속을 찾고 있습니다.", "금액과 할 일을 정리하고 있습니다.", "거래처 기록과 맞춰 보고 있습니다. 거의 끝났습니다."][step]} />
    <div class="raw">${initial.text.slice(0, 1500)}</div>
    <div class="panel steps">${STEPS.map((s, i) => html`<div class=${"step " + (i < step ? "done" : i === step ? "now" : "")}><span class="d"></span>${s}</div>`)}</div>
  </div>`;
  if (phase === "failed") return html`<div><${Band} text="분석하지 못했습니다." /><div class="panel"><div style="color:var(--red)">${err}</div><div class="actions"><button class="btn" onClick=${onDone}>닫기</button></div></div></div>`;
  if (phase === "saved") return html`<div><${Band} text="등록했습니다. 폰 앱이 다음에 열릴 때 일정을 삼성 캘린더에 넣습니다." /><div class="actions"><button class="btn primary" onClick=${onDone}>닫기</button></div></div>`;

  const save = async () => {
    setSaving(true);
    try { await store.save(buildItems(d, store, initial.pendingUid)); setPhase("saved"); }
    catch (e) { toast("저장 실패: " + e.message); setSaving(false); }
  };
  const discard = async () => {
    if (initial.pendingUid) await store.save([{ kind: "pending", uid: initial.pendingUid, deleted: true, data: {} }]);
    onDone();
  };
  const askReply = async () => {
    if (d.replyDraft && !reply) { setReply(d.replyDraft); return; }
    try { const r = await api.draftReply(toAnalysis(d)); setReply(r.reply); } catch (e) { toast("답장 초안 실패: " + e.message); }
  };

  return html`<div>
    <${Band} kicker=${[d.orgName, d.person, L.orgKindName(d.orgKind)].filter(Boolean).join(", ") + (d.confidence < 0.7 ? `  (확신도 ${Math.round(d.confidence * 100)}%)` : "")} text=${readySentence(d)} />
    <button class="linkish" onClick=${() => setShowRaw(!showRaw)}>${showRaw ? "원문 닫기" : `원문 보기 (${L.sourceName(d.source)})`}</button>
    ${showRaw && html`<div class="raw" style="margin-top:8px">${d.rawText}</div>`}
    <div class="panel">
      <h3>소속</h3><${Chips} options=${L.ENTITIES.map((e) => [e, e])} value=${d.entity} onChange=${(v) => set({ entity: v })} />
      <div class="grid2" style="margin-top:12px">
        <${Field} label="상대 조직" value=${d.orgName} onChange=${(v) => set({ orgName: v })} />
        <${Field} label="담당자" value=${d.person} onChange=${(v) => set({ person: v })} />
        <${Select} label="구분" options=${L.ORG_KINDS} value=${d.orgKind} onChange=${(v) => set({ orgKind: v })} />
        <${Field} label="사업 현장명" value=${d.projectName} onChange=${(v) => set({ projectName: v })} />
        <${Field} label="현장(역)" value=${d.site} onChange=${(v) => set({ site: v })} />
        <${Select} label="시스템" options=${L.SYSTEMS} value=${d.system} onChange=${(v) => set({ system: v })} />
        <${Select} label="유형" options=${L.TYPES} value=${d.type} onChange=${(v) => set({ type: v, createTicket: v === "fault" })} />
        <${Select} label="긴급도" options=${L.URGENCIES} value=${d.urgency} onChange=${(v) => set({ urgency: v })} />
      </div>
      <label class="f" style="margin-top:10px">요약<textarea value=${d.summary} onInput=${(e) => set({ summary: e.target.value })}></textarea></label>
    </div>
    ${d.decisions.length > 0 && html`<div class="panel"><h3>결정 사항</h3>${d.decisions.map((x) => html`<div>• ${x}</div>`)}</div>`}
    ${d.questions.length > 0 && html`<div class="q">${d.questions.map((x) => html`<div>? ${x}</div>`)}</div>`}
    <div class="panel">
      <h3>등록할 항목  (체크를 끄면 등록하지 않음)</h3>
      ${d.events.map((e, i) => html`<label class="check"><input type="checkbox" checked=${e.include} onChange=${() => toggle("events", i)} /><div><div class="kick signal">삼성 캘린더 일정 (폰에서 등록)</div><div class="ti">${e.title}</div><div class="sub">${[L.fmtDateTime(L.toMillis(e.start)), e.place].filter(Boolean).join(", ")}</div></div></label>`)}
      ${(d.type === "fault" || d.createTicket) && html`<label class="check"><input type="checkbox" checked=${d.createTicket} onChange=${() => set({ createTicket: !d.createTicket })} /><div><div class="kick red">장애 티켓</div><div class="ti">${d.summary}</div></div></label>`}
      ${d.tasks.map((t, i) => html`<label class="check"><input type="checkbox" checked=${t.include} onChange=${() => toggle("tasks", i)} /><div><div class="kick">할 일, ${L.ownerName(t.owner)}${t.due ? ", " + L.fmtDate(t.due) : ""}</div><div class="ti">${t.title}</div>${t.note && html`<div class="sub">${t.note}</div>`}</div></label>`)}
      ${d.money.map((m, i) => html`<label class="check"><input type="checkbox" checked=${m.include} onChange=${() => toggle("money", i)} /><div><div class="kick green">${L.moneyKindName(m.kind)}${m.due ? ", " + L.fmtDate(m.due) : ""}</div><div class="ti">${L.won(m.amount)}</div>${m.note && html`<div class="sub">${m.note}</div>`}</div></label>`)}
      ${!d.events.length && !d.tasks.length && !d.money.length && !d.createTicket && html`<div class="empty">등록할 일정이나 할 일이 없습니다. 기록만 저장됩니다.</div>`}
    </div>
    ${reply && html`<div class="panel"><h3>확인 문자 초안</h3><div>${reply}</div><div class="actions"><button class="btn small" onClick=${() => navigator.clipboard?.writeText(reply)}>복사</button></div></div>`}
    <div class="actions">
      <button class="btn primary" onClick=${save} disabled=${saving}>${saving ? "저장 중" : "등록"}</button>
      <button class="btn" onClick=${askReply}>답장 초안</button>
      <button class="btn ghost" onClick=${discard}>사업 무관으로 버리기</button>
    </div>
  </div>`;
}

/* ---------- 정리하기 (입력) ---------- */
function Input({ store, toast, onDone }) {
  const [text, setText] = useState("");
  const [source, setSource] = useState("call");
  const [job, setJob] = useState(null);
  if (job) return html`<${Confirm} store=${store} initial=${job} toast=${toast} onDone=${() => { setJob(null); setText(""); onDone && onDone(); }} />`;
  return html`<div>
    <${Band} text="무엇을 정리할까요? 갤럭시 AI 통화 요약이나 문자, 카톡 내용을 {붙여넣으면} 거래처 기록과 일정으로 만들어 드립니다." />
    <div class="panel">
      <${Chips} options=${L.SOURCES.filter(([c]) => c !== "visit")} value=${source} onChange=${setSource} />
      <textarea style="margin-top:10px;min-height:220px" placeholder="예: 군자역 김 과장, 내일 오전 10시 B2 조명 제어기 점검 방문" value=${text} onInput=${(e) => setText(e.target.value)}></textarea>
      <div class="actions"><button class="btn primary" disabled=${!text.trim()} onClick=${() => setJob({ text: text.trim(), source })}>분석</button></div>
    </div>
  </div>`;
}

/* ---------- 오늘 ---------- */
function Today({ store, go, toast, openPending }) {
  const today = L.todayKst();
  const dayStart = L.startOfDayMs(today), dayEnd = L.startOfDayMs(L.addDays(today, 1)), weekEnd = L.addDays(today, 7);
  const events = store.live("event").filter((e) => e.startMillis >= dayStart && e.startMillis < dayEnd).sort((a, b) => a.startMillis - b.startMillis);
  const tomorrow = store.live("event").filter((e) => e.startMillis >= dayEnd && e.startMillis < L.startOfDayMs(L.addDays(today, 2)));
  const tasks = store.live("task").filter((t) => !t.done);
  const dueSoon = tasks.filter((t) => t.due && t.due.slice(0, 10) <= weekEnd).sort((a, b) => a.due.localeCompare(b.due)).slice(0, 8);
  const openTickets = store.live("ticket").filter((t) => t.status !== "done").length;
  const receivable = store.live("money").filter((m) => m.kind === "receivable" && !m.settled && m.due && m.due <= weekEnd).reduce((s, m) => s + (m.amount || 0), 0);
  const recent = store.live("record").sort((a, b) => b.createdAt - a.createdAt).slice(0, 8);
  const pending = store.live("pending").sort((a, b) => b.createdAt - a.createdAt);
  const now = Date.now();
  const next = events.find((e) => e.startMillis > now);

  let sentence = "";
  if (pending.length) sentence += `확인을 기다리는 통화 정리가 {${pending.length}건} 있습니다. `;
  if (!recent.length && !events.length && !tasks.length && !pending.length) sentence += "아직 정리한 기록이 없습니다. 왼쪽 {정리하기}에서 통화 요약을 붙여넣어 보세요.";
  else {
    sentence += events.length ? `오늘 일정 {${events.length}건}이 있습니다.` : "오늘 잡힌 일정은 없습니다.";
    if (next) sentence += ` 다음은 {${L.fmtTime(next.startMillis)} ${next.title}}입니다.`;
    if (tomorrow[0]) sentence += ` 내일은 {${tomorrow[0].title}}이 잡혀 있습니다.`;
    if (openTickets) sentence += ` 미완료 장애가 {${openTickets}건} 있습니다.`;
  }
  const setDone = (t, done) => store.save([{ kind: "task", uid: t.uid, data: { ...stripMeta(t), done } }]).catch((e) => toast(e.message));

  return html`<div>
    <${Band} kicker=${L.fmtToday()} text=${sentence} />
    ${pending.length > 0 && html`<div class="sec"><h2>확인 대기</h2><div class="rail-list">${pending.map((p) => html`<div class="it active clickable" onClick=${() => openPending(p)}><div class="kick signal">${p.callerHint || L.sourceName(p.source)}</div><div class="ti">${p.title}</div><div class="sub">${L.fmtDateTime(p.createdAt)}  확인하려면 누르세요</div></div>`)}</div></div>`}
    <div class="two">
      <div>
        <div class="sec"><h2>오늘</h2>${events.length ? html`<div class="rail-list">${events.map((e) => html`<div class=${"it" + (next === e ? " active" : "")}><div class="row"><div class="tm">${L.fmtTime(e.startMillis)}</div><div><div class="ti">${e.title}</div>${e.place && html`<div class="sub">${e.place}</div>`}</div></div></div>`)}</div>` : html`<div class="empty">등록된 일정이 없습니다.</div>`}</div>
        <div class="sec"><h2>기한이 가까운 할 일</h2>${dueSoon.length ? html`<div class="rail-list">${dueSoon.map((t) => { const dl = L.dueLabel(t.due); return html`<div class=${"it " + (dl && dl[1] === "red" ? "red" : "")}><label class="check" style="padding:0;border:0"><input type="checkbox" checked=${t.done} onChange=${(e) => setDone(t, e.target.checked)} /><div><div class="ti">${t.title}</div><div class="meta">${dl && html`<${Pill} tone=${dl[1]}>${dl[0]}</${Pill}>`}<span>${[t.owner !== "me" ? L.ownerName(t.owner) : null, t.note].filter(Boolean).join(", ")}</span></div></div></label></div>`; })}</div>` : html`<div class="empty">일주일 안에 기한이 오는 할 일이 없습니다.</div>`}</div>
        <div class="stats">
          <div class="st" style="cursor:pointer" onClick=${() => go("tickets")}><div class=${"n" + (openTickets ? " red" : "")}>${openTickets}</div><div class="l">미완료 장애</div></div>
          <div class="st"><div class=${"n" + (receivable ? " green" : "")}>${receivable ? shortWon(receivable) : "0"}</div><div class="l">이번 주 받을 돈</div></div>
          <div class="st"><div class="n">${tomorrow.length}</div><div class="l">내일 일정</div></div>
        </div>
      </div>
      <div class="sec"><h2>최근 정리한 기록</h2>${recent.length ? html`<div class="rail-list">${recent.map((r) => html`<${RecordCard} r=${r} store=${store} toast=${toast} compact=${true} />`)}</div>` : html`<div class="empty">아직 없습니다.</div>`}</div>
    </div>
  </div>`;
}
const shortWon = (n) => (n >= 1e8 ? html`${(n / 1e8).toFixed(1)}<small>억</small>` : n >= 1e4 ? html`${Math.floor(n / 1e4)}<small>만</small>` : html`${n}<small>원</small>`);
const stripMeta = ({ uid, updatedAt, ...data }) => data;

/* ---------- 기록 카드 (보기 + 수정 + 삭제) ---------- */
function RecordCard({ r, store, toast, compact }) {
  const [open, setOpen] = useState(false);
  const [edit, setEdit] = useState(false);
  const [form, setForm] = useState(null);
  const events = store.live("event").filter((e) => e.recordUid === r.uid);
  const tasks = store.live("task").filter((t) => t.recordUid === r.uid);
  const money = store.live("money").filter((m) => m.recordUid === r.uid);
  const startEdit = (ev) => { ev.stopPropagation(); setForm({ summary: r.summary, decisions: (r.decisions || []).join("\n"), type: r.type, urgency: r.urgency, site: r.site || "", projectName: r.projectName || "" }); setEdit(true); };
  const saveEdit = async () => {
    await store.save([{ kind: "record", uid: r.uid, data: { ...stripMeta(r), summary: form.summary, decisions: form.decisions.split("\n").map((s) => s.trim()).filter(Boolean), type: form.type, urgency: form.urgency, site: form.site || null, projectName: form.projectName || null } }]).catch((e) => toast(e.message));
    setEdit(false);
  };
  const remove = async (ev) => {
    ev.stopPropagation();
    if (!confirm("이 기록과 연결된 일정, 할 일, 금전을 모두 지울까요?")) return;
    const del = [{ kind: "record", uid: r.uid, deleted: true, data: {} }];
    for (const e of events) del.push({ kind: "event", uid: e.uid, deleted: true, data: {} });
    for (const t of tasks) del.push({ kind: "task", uid: t.uid, deleted: true, data: {} });
    for (const m of money) del.push({ kind: "money", uid: m.uid, deleted: true, data: {} });
    for (const t of store.live("ticket").filter((t) => t.recordUid === r.uid)) del.push({ kind: "ticket", uid: t.uid, deleted: true, data: {} });
    await store.save(del).catch((e) => toast(e.message));
  };
  return html`<div class=${"it clickable" + (r.urgency === "high" ? " red" : "")} onClick=${() => setOpen(!open)}>
    <div class="meta"><span>${L.sourceName(r.source)}  ${L.fmtDateTime(r.createdAt)}</span><span class="right"></span><${TypePill} type=${r.type} /><${EntityPill} entity=${r.entity} /></div>
    <div class="sub" style="margin-top:4px">${[r.orgName, r.person].filter(Boolean).join(" ") || "상대 미상"}${r.projectName ? `, ${r.projectName}` : ""}${r.site ? `, ${r.site}` : ""}</div>
    ${edit ? html`<div onClick=${(e) => e.stopPropagation()} style="margin-top:8px">
        <label class="f">요약<textarea value=${form.summary} onInput=${(e) => setForm({ ...form, summary: e.target.value })}></textarea></label>
        <label class="f" style="margin-top:8px">결정 사항 (줄마다 하나)<textarea value=${form.decisions} onInput=${(e) => setForm({ ...form, decisions: e.target.value })}></textarea></label>
        <div class="grid2" style="margin-top:8px"><${Select} label="유형" options=${L.TYPES} value=${form.type} onChange=${(v) => setForm({ ...form, type: v })} /><${Select} label="긴급도" options=${L.URGENCIES} value=${form.urgency} onChange=${(v) => setForm({ ...form, urgency: v })} /><${Field} label="현장" value=${form.site} onChange=${(v) => setForm({ ...form, site: v })} /><${Field} label="사업명" value=${form.projectName} onChange=${(v) => setForm({ ...form, projectName: v })} /></div>
        <div class="actions"><button class="btn primary small" onClick=${saveEdit}>저장</button><button class="btn ghost small" onClick=${() => setEdit(false)}>취소</button></div>
      </div>`
      : html`<div style="margin-top:6px">${r.summary}</div>
        ${!compact && (r.decisions || []).map((x) => html`<div class="sub">• ${x}</div>`)}
        ${!compact && (r.questions || []).map((x) => html`<div class="sub" style="color:var(--amber)">? ${x}</div>`)}`}
    ${(events.length || tasks.length || money.length) > 0 && html`<div class="meta" style="margin-top:8px;gap:6px">
      ${events.map((e) => html`<${Pill} tone="signal">${L.fmtDateTime(e.startMillis)}  ${e.title}</${Pill}>`)}
      ${tasks.map((t) => html`<${Pill}>${t.done ? "완료  " : ""}${t.title}${t.due ? ", " + L.fmtDate(t.due) : ""}</${Pill}>`)}
      ${money.map((m) => html`<${Pill} tone="green">${L.moneyKindName(m.kind)}  ${L.won(m.amount)}</${Pill}>`)}
    </div>`}
    ${open && !edit && html`<div onClick=${(e) => e.stopPropagation()}>
      <div class="raw" style="margin-top:10px">${r.rawText}</div>
      <div class="actions"><button class="btn small" onClick=${startEdit}>수정</button><button class="btn danger small" onClick=${remove}>삭제</button></div>
    </div>`}
  </div>`;
}

/* ---------- 거래처 ---------- */
function Orgs({ store, toast }) {
  const orgs = store.live("org").sort((a, b) => (b.lastContactAt || 0) - (a.lastContactAt || 0));
  const [sel, setSel] = useState(null);
  const [filter, setFilter] = useState(null);
  const selected = orgs.find((o) => o.uid === sel) || null;
  const stale = orgs.filter((o) => o.lastContactAt && Date.now() - o.lastContactAt > 14 * 86400000);
  let sentence = orgs.length ? `거래처 {${orgs.length}곳}을 기억하고 있습니다.` : "아직 기억하는 거래처가 없습니다. 첫 통화를 정리하면 여기에 자동으로 생깁니다.";
  if (stale[0]) sentence += ` {${stale[0].name}}와는 ${Math.floor((Date.now() - stale[0].lastContactAt) / 86400000)}일째 연락이 없습니다.`;
  const groups = L.ORG_KINDS;
  const records = selected ? store.live("record").filter((r) => r.orgUid === selected.uid && (!filter || r.entity === filter)).sort((a, b) => b.createdAt - a.createdAt) : [];
  return html`<div>
    <${Band} kicker="거래처" text=${sentence} />
    <div class="two">
      <div>${groups.map(([kind, title]) => { const list = orgs.filter((o) => o.kind === kind); return list.length ? html`<div class="sec"><h2>${title}</h2><div class="rail-list">${list.map((o) => { const days = o.lastContactAt ? Math.floor((Date.now() - o.lastContactAt) / 86400000) : null; return html`<div class=${"it clickable" + (sel === o.uid ? " active" : days >= 14 ? " amber" : "")} onClick=${() => setSel(o.uid)}><div class="row" style="justify-content:space-between"><span class="ti">${o.name}</span><span class="sub" style=${days >= 14 ? "color:var(--amber)" : ""}>${o.lastContactAt ? L.relative(o.lastContactAt) : "기록 없음"}</span></div>${o.lastPerson && html`<div class="sub">${o.lastPerson}</div>`}</div>`; })}</div></div>` : null; })}</div>
      <div>${selected ? html`
        <div class="panel"><div class="sub">${L.orgKindName(selected.kind)}</div><div style="font-size:20px;font-weight:600">${selected.name}</div><div class="sub">${[selected.lastPerson, selected.lastContactAt && "마지막 연락 " + L.fmtDateTime(selected.lastContactAt)].filter(Boolean).join(", ")}</div>
          <div class="chips" style="margin-top:10px"><button class=${"chip" + (!filter ? " on" : "")} onClick=${() => setFilter(null)}>전체</button>${L.ENTITIES.map((e) => html`<button class=${"chip" + (filter === e ? " on" : "")} onClick=${() => setFilter(e)}>${e}</button>`)}</div></div>
        ${records.length ? html`<div class="rail-list">${records.map((r) => html`<${RecordCard} r=${r} store=${store} toast=${toast} />`)}</div>` : html`<div class="empty">기록이 없습니다.</div>`}`
        : html`<div class="empty">왼쪽에서 거래처를 고르면 기록이 여기 나옵니다.</div>`}</div>
    </div>
  </div>`;
}

/* ---------- 장애 ---------- */
function Tickets({ store, toast }) {
  const all = store.live("ticket");
  const open = all.filter((t) => t.status !== "done");
  const urgent = open.filter((t) => t.urgency === "high").length, waiting = open.filter((t) => t.status === "open").length;
  let sentence = !all.length ? "접수된 장애가 없습니다." : !open.length ? `미완료 장애가 없습니다. 완료 {${all.length}건}입니다.` : `미완료 장애 {${open.length}건}입니다.${urgent ? ` 그중 긴급이 {${urgent}건}` : ""}${waiting ? `${urgent ? "," : ""} 아직 방문 일정이 없는 건이 {${waiting}건}입니다.` : urgent ? "입니다." : ""}`;
  const advance = (t) => { const next = t.status === "open" ? "scheduled" : t.status === "scheduled" ? "working" : "done"; store.save([{ kind: "ticket", uid: t.uid, data: { ...stripMeta(t), status: next, closedAt: next === "done" ? Date.now() : null } }]).catch((e) => toast(e.message)); };
  return html`<div>
    <${Band} kicker="장애와 AS" text=${sentence} />
    <div class="cols">${L.TICKET_STATUSES.map(([code, title]) => { const list = all.filter((t) => t.status === code).sort((a, b) => b.createdAt - a.createdAt); return html`<div class="sec"><h2>${title}  ${list.length}</h2>${list.length ? html`<div class="rail-list">${list.map((t) => html`<div class=${"it" + (t.status !== "done" && t.urgency === "high" ? " red active" : "")}><div class="meta"><span>${L.relative(t.createdAt)}</span><span class="right" style=${"color:" + (t.urgency === "high" ? "var(--red)" : "var(--muted)")}>${L.urgencyName(t.urgency)}</span></div><div class="ti">${[t.site, t.system !== "none" && L.systemName(t.system)].filter(Boolean).join(", ") || "현장 미상"}</div><div class="sub">${t.symptom}</div><div class="meta" style="margin-top:6px"><${EntityPill} entity=${t.entity} />${t.orgName}</div>${t.status !== "done" ? html`<div class="actions"><button class="btn small" onClick=${() => advance(t)}>${t.status === "open" ? "방문 일정 잡음" : t.status === "scheduled" ? "조치 시작" : "조치 완료"}</button></div>` : t.closedAt && html`<div class="sub" style="color:var(--green)">완료 ${L.fmtDateTime(t.closedAt)}</div>`}</div>`)}</div>` : html`<div class="empty">없음</div>`}</div>`; })}</div>
  </div>`;
}

/* ---------- 할 일 ---------- */
function Tasks({ store, toast }) {
  const [showDone, setShowDone] = useState(false);
  const all = store.live("task");
  const open = all.filter((t) => !t.done).sort((a, b) => (a.due || "9999").localeCompare(b.due || "9999"));
  const done = all.filter((t) => t.done).sort((a, b) => b.updatedAt - a.updatedAt).slice(0, 30);
  const recordOf = (t) => store.live("record").find((r) => r.uid === t.recordUid);
  const overdue = open.filter((t) => t.due && t.due.slice(0, 10) < L.todayKst()).length;
  const sentence = open.length ? `남은 할 일이 {${open.length}건} 있습니다.${overdue ? ` 그중 {${overdue}건}은 기한이 지났습니다.` : ""}` : "남은 할 일이 없습니다.";
  const update = (t, patch) => store.save([{ kind: "task", uid: t.uid, data: { ...stripMeta(t), ...patch } }]).catch((e) => toast(e.message));
  const Row = ({ t }) => {
    const [edit, setEdit] = useState(false); const [title, setTitle] = useState(t.title); const [due, setDue] = useState(t.due || "");
    const dl = L.dueLabel(t.due); const r = recordOf(t);
    return html`<div class=${"it " + (dl && dl[1] === "red" && !t.done ? "red" : "")}>
      <label class="check" style="padding:0;border:0"><input type="checkbox" checked=${!!t.done} onChange=${(e) => update(t, { done: e.target.checked })} />
        <div style="flex:1">${edit ? html`<div class="edit-row"><input type="text" value=${title} onInput=${(e) => setTitle(e.target.value)} /><input type="date" value=${due} onInput=${(e) => setDue(e.target.value)} style="width:160px" /><button class="btn small primary" onClick=${() => { update(t, { title, due: due || null }); setEdit(false); }}>저장</button><button class="btn small ghost" onClick=${() => setEdit(false)}>취소</button></div>`
          : html`<div class="ti" style=${t.done ? "color:var(--muted);text-decoration:line-through" : ""}>${t.title}</div>
            <div class="meta">${dl && !t.done && html`<${Pill} tone=${dl[1]}>${dl[0]}</${Pill}>`}<span>${[t.due && L.fmtDate(t.due), t.owner !== "me" ? L.ownerName(t.owner) : null, r && r.orgName, t.note].filter(Boolean).join(", ")}</span><button class="linkish right" onClick=${(e) => { e.preventDefault(); setEdit(true); }}>수정</button></div>`}
        </div></label></div>`;
  };
  return html`<div>
    <${Band} kicker="할 일" text=${sentence} />
    <div class="sec"><h2>남은 할 일</h2>${open.length ? html`<div class="rail-list">${open.map((t) => html`<${Row} t=${t} key=${t.uid} />`)}</div>` : html`<div class="empty">없음</div>`}</div>
    <div class="sec"><h2><button class="linkish" onClick=${() => setShowDone(!showDone)}>${showDone ? "완료한 할 일 숨기기" : `완료한 할 일 보기 (${done.length})`}</button></h2>${showDone && html`<div class="rail-list">${done.map((t) => html`<${Row} t=${t} key=${t.uid} />`)}</div>`}</div>
  </div>`;
}

/* ---------- 설정 ---------- */
function Settings({ store, onLogout }) {
  const [stats, setStats] = useState(null);
  const [health, setHealth] = useState(null);
  useEffect(() => { api.stats().then(setStats).catch(() => {}); api.health().then(setHealth).catch(() => {}); }, []);
  return html`<div>
    <${Band} kicker="설정" text=${health ? (health.ok ? `서버가 {정상}입니다. 분석 모델 ${health.model}, 음성 인식 ${health.transcribe ? "{준비됨}" : "꺼짐"}.` : "서버 설정에 문제가 있습니다.") : "서버 상태를 확인하고 있습니다."} />
    <div class="panel"><h3>저장된 기록</h3>${stats ? html`<div class="meta">${Object.entries(stats).map(([k, n]) => html`<${Pill}>${({ org: "거래처", project: "사업", record: "기록", event: "일정", task: "할 일", money: "금전", ticket: "장애", pending: "대기" })[k] || k} ${n}</${Pill}>`)}</div>` : html`<div class="sub">불러오는 중</div>`}
      <div class="sub" style="margin-top:8px">마지막 동기화 커서 ${store.cursor ? L.fmtDateTime(store.cursor) : "없음"}. 45초마다 새 기록을 받아옵니다.</div></div>
    <div class="panel"><h3>이 컴퓨터</h3><div class="sub">서버 토큰은 이 브라우저에만 저장됩니다. 공용 컴퓨터라면 로그아웃하세요.</div><div class="actions"><button class="btn" onClick=${onLogout}>로그아웃</button></div></div>
  </div>`;
}

/* ---------- 전체 ---------- */
const NAV = [["today", "오늘"], ["orgs", "거래처"], ["tickets", "장애"], ["tasks", "할 일"], ["input", "정리하기"], ["settings", "설정"]];

function App() {
  const [authed, setAuthed] = useState(!!getToken());
  if (!authed) return html`<${Login} onDone=${() => setAuthed(true)} />`;
  return html`<${Shell} onLogout=${() => { setToken(""); setAuthed(false); }} />`;
}

function Shell({ onLogout }) {
  const store = useStore();
  const [view, setView] = useState(location.hash.replace("#", "") || "today");
  const [pendingJob, setPendingJob] = useState(null);
  const [toastMsg, setToastMsg] = useState(null);
  const toast = (m) => { setToastMsg(m); setTimeout(() => setToastMsg(null), 4000); };
  const go = (v) => { setView(v); location.hash = v; setPendingJob(null); };
  useEffect(() => { if (store.error && store.error.status === 401) onLogout(); }, [store.error]);
  const openPending = (p) => {
    let analysis = null; try { analysis = JSON.parse(p.analysisJson); } catch {}
    setPendingJob({ text: p.rawText, source: p.source, analysis, pendingUid: p.uid });
  };
  const pendingCount = store.live("pending").length;
  const openCount = store.live("ticket").filter((t) => t.status !== "done").length;
  let body;
  if (pendingJob) body = html`<${Confirm} store=${store} initial=${pendingJob} toast=${toast} onDone=${() => setPendingJob(null)} />`;
  else if (store.status === "loading") body = html`<${Band} text="기록을 불러오고 있습니다." />`;
  else if (store.status === "error") body = html`<div><${Band} text="서버에서 기록을 받지 못했습니다." /><div class="panel"><div style="color:var(--amber)">${store.error?.message}</div><div class="actions"><button class="btn" onClick=${store.pull}>다시 시도</button></div></div></div>`;
  else body = { today: html`<${Today} store=${store} go=${go} toast=${toast} openPending=${openPending} />`, orgs: html`<${Orgs} store=${store} toast=${toast} />`, tickets: html`<${Tickets} store=${store} toast=${toast} />`, tasks: html`<${Tasks} store=${store} toast=${toast} />`, input: html`<${Input} store=${store} toast=${toast} onDone=${() => go("today")} />`, settings: html`<${Settings} store=${store} onLogout=${onLogout} />` }[view] || html`<${Today} store=${store} go=${go} toast=${toast} openPending=${openPending} />`;
  return html`<div class="shell">
    <nav class="rail">
      <div class="brand"><div class="ring" style="width:22px;height:22px;margin:0"></div>사업 비서</div>
      ${NAV.map(([v, name]) => html`<button class=${view === v && !pendingJob ? "active" : ""} onClick=${() => go(v)}>${name}${v === "today" && pendingCount ? html`<${Pill} tone="signal">${pendingCount}</${Pill}>` : v === "tickets" && openCount ? html`<${Pill} tone="red">${openCount}</${Pill}>` : ""}</button>`)}
      <div class="spacer"></div>
      <div class="foot">폰 앱과 같은 기록을 봅니다. 폰에서 등록한 내용은 45초 안에 여기 나타납니다.</div>
    </nav>
    <main class="main">${body}</main>
    ${toastMsg && html`<div class="toast">${toastMsg}</div>`}
  </div>`;
}

render(html`<${App} />`, document.getElementById("app"));
