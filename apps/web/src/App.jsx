import React, { useEffect, useMemo, useState } from "react";
import { SignInButton, SignedIn, SignedOut, UserButton, useAuth } from "@clerk/clerk-react";
import { apiRequest } from "./api.js";

// ── Constants ──
const TABS = ["Calendar", "Events", "School Days", "Proposals", "Solve", "Ledger", "Activity"];
const DAY_NAMES = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

const defaultSettings = {
  baseUrl: "",
  token: "",
  caseId: "",
  subjectOverride: ""
};

// ── Utilities ──
function resolveBaseUrl(settings) {
  if (settings.baseUrl) return settings.baseUrl;
  if (typeof window !== "undefined" && window.location.hostname !== "localhost") {
    return window.location.origin;
  }
  return "http://localhost:8080";
}

function loadStoredSettings() {
  try {
    const stored = JSON.parse(localStorage.getItem("cc-settings"));
    return { ...defaultSettings, ...stored };
  } catch {
    return defaultSettings;
  }
}

function persistSettings(s) {
  localStorage.setItem("cc-settings", JSON.stringify(s));
}

function toDateInput(date) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

function startOfMonth(date) {
  return new Date(date.getFullYear(), date.getMonth(), 1);
}

function endOfMonth(date) {
  return new Date(date.getFullYear(), date.getMonth() + 1, 0);
}

function addDays(date, count) {
  const r = new Date(date);
  r.setDate(r.getDate() + count);
  return r;
}

function fmtMonth(date) {
  return date.toLocaleDateString("en-US", { month: "long", year: "numeric" });
}

function shortId(v) {
  return v ? String(v).slice(0, 6) : "";
}

function defaultTz() {
  try { return Intl.DateTimeFormat().resolvedOptions().timeZone || "America/Los_Angeles"; }
  catch { return "America/Los_Angeles"; }
}

function decodeJwt(token) {
  if (!token || !token.includes(".")) return null;
  try {
    const p = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    return JSON.parse(atob(p.padEnd(Math.ceil(p.length / 4) * 4, "=")));
  } catch { return null; }
}

function scoreLabel(key) {
  const m = { transitions: "Transitions", schoolNightTransitions: "School-night transitions", parityDrift: "Parity drift", lockedProximity: "Near locked days", owedImbalance: "Owed imbalance", runDaysOverThree: "Long runs (>3)" };
  return m[key] || key;
}

function fmtBucket(b) {
  if (b === "SCHOOL") return "school";
  if (b === "NON_SCHOOL") return "non-school";
  return (b || "").toLowerCase();
}

function getMonthGrid(date) {
  const start = startOfMonth(date);
  const end = endOfMonth(date);
  const offset = (start.getDay() + 6) % 7;
  const days = [];
  for (let i = 0; i < offset; i++) days.push({ date: addDays(start, i - offset), current: false });
  for (let d = 0; d < end.getDate(); d++) days.push({ date: addDays(start, d), current: true });
  while (days.length % 7 !== 0) days.push({ date: addDays(end, days.length - (offset + end.getDate()) + 1), current: false });
  return days;
}

function relativeTime(iso) {
  if (!iso) return "";
  const d = new Date(iso);
  const diff = Date.now() - d.getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 1) return "just now";
  if (mins < 60) return `${mins}m ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs}h ago`;
  return d.toLocaleDateString();
}

// ── Entry points ──
export default function App() {
  const clerkConfigured = Boolean(import.meta.env.VITE_CLERK_PUBLISHABLE_KEY);
  return clerkConfigured ? <AppWithClerk /> : <AppCore clerkConfigured={false} auth={null} clerkJwtTemplate="" />;
}

function AppWithClerk() {
  const auth = useAuth();
  const t = import.meta.env.VITE_CLERK_JWT_TEMPLATE || "";
  return <AppCore clerkConfigured auth={auth} clerkJwtTemplate={t} />;
}

// ── Main App ──
function AppCore({ clerkConfigured, auth, clerkJwtTemplate }) {
  const [settings, setSettings] = useState(loadStoredSettings);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [activeTab, setActiveTab] = useState("Calendar");
  const [status, setStatus] = useState("Idle");
  const [members, setMembers] = useState([]);
  const [events, setEvents] = useState([]);
  const [scheduleDays, setScheduleDays] = useState([]);
  const [schoolDays, setSchoolDays] = useState([]);
  const [calendarMonth, setCalendarMonth] = useState(new Date());
  const [solveOptions, setSolveOptions] = useState([]);
  const [solveRequest, setSolveRequest] = useState(null);
  const [selectedOption, setSelectedOption] = useState(null);
  const [error, setError] = useState("");
  const [cases, setCases] = useState([]);
  const [createCaseForm, setCreateCaseForm] = useState({ name: "", timezone: defaultTz() });
  const [inviteMemberForm, setInviteMemberForm] = useState({ externalSubject: "", displayName: "", role: "PARENT" });
  const [scheduleRule, setScheduleRule] = useState(null);
  const [scheduleRuleForm, setScheduleRuleForm] = useState({ anchorDate: "", parentAId: "", parentBId: "", pattern: "AABBAAABBAABBBB" });
  const [proposals, setProposals] = useState([]);
  const [previewProposalId, setPreviewProposalId] = useState(null);
  const [identities, setIdentities] = useState([]);
  const [linkForm, setLinkForm] = useState({ externalSubject: "", label: "" });
  const [ledgerEntries, setLedgerEntries] = useState([]);
  const [ledgerBalances, setLedgerBalances] = useState([]);
  const [auditEntries, setAuditEntries] = useState([]);

  // Resolved API settings
  function api() {
    return { ...settings, baseUrl: resolveBaseUrl(settings) };
  }

  const memberMap = useMemo(() => {
    const m = new Map();
    members.forEach(p => m.set(p.personId, p.displayName));
    return m;
  }, [members]);

  const claims = useMemo(() => decodeJwt(settings.token), [settings.token]);
  const todayStr = toDateInput(new Date());

  useEffect(() => { persistSettings(settings); }, [settings]);

  // Clerk token sync
  useEffect(() => {
    if (!clerkConfigured || !auth) return;
    let c = false;
    (async () => {
      if (!auth.isSignedIn) { if (!c) setSettings(p => p.token ? { ...p, token: "" } : p); return; }
      const t = await auth.getToken(clerkJwtTemplate ? { template: clerkJwtTemplate } : undefined);
      if (!c) setSettings(p => p.token === (t || "") ? p : { ...p, token: t || "" });
    })().catch(() => { if (!c) { setError("Failed to get Clerk token."); setStatus("Error"); } });
    return () => { c = true; };
  }, [auth, clerkConfigured, clerkJwtTemplate]);

  // Load cases on token change
  useEffect(() => {
    if (!settings.token) { setCases([]); return; }
    withStatus("Loading", loadCases);
  }, [settings.baseUrl, settings.token]);

  // Auto-select single case
  useEffect(() => {
    if (cases.length === 1 && !settings.caseId) {
      setSettings(p => ({ ...p, caseId: cases[0].id }));
    }
  }, [cases]);

  // Load members + rule when case changes
  useEffect(() => {
    if (settings.caseId && settings.token) {
      loadMembers().then(() => loadScheduleRule()).catch(() => {});
    } else {
      setMembers([]);
      setScheduleRule(null);
    }
  }, [settings.caseId, settings.token]);

  // Linked logins for the settings drawer
  useEffect(() => {
    if (settingsOpen && settings.token) {
      loadIdentities().catch(() => {});
    }
  }, [settingsOpen, settings.token, settings.subjectOverride]);

  // Tab data loading
  useEffect(() => {
    if (!settings.caseId) return;
    if (activeTab === "Calendar") withStatus("Loading calendar", refreshCalendar);
    if (activeTab === "Solve") withStatus("Loading", async () => { await loadMembers(); await loadScheduleRule(); });
    if (activeTab === "Proposals") withStatus("Loading proposals", loadProposals);
    if (activeTab === "Ledger") withStatus("Loading ledger", async () => { await loadMembers(); await loadLedger(); });
    if (activeTab === "Activity") withStatus("Loading activity", async () => { await loadMembers(); await loadAudit(); });
  }, [activeTab, calendarMonth, settings.caseId]);

  // ── Status wrapper ──
  async function withStatus(label, fn) {
    setStatus(label);
    setError("");
    try { await fn(); setStatus("Ready"); }
    catch (err) { setError(err.message || String(err)); setStatus("Error"); }
  }

  // ── API functions ──
  async function loadMembers() {
    if (!settings.caseId) return;
    setMembers(await apiRequest(`/api/v1/cases/${settings.caseId}/people`, api()));
  }

  async function loadCases() {
    if (!settings.token) { setCases([]); return; }
    setCases((await apiRequest("/api/v1/cases", api())) || []);
  }

  async function createCase() {
    const p = { name: createCaseForm.name.trim(), timezone: createCaseForm.timezone.trim() };
    if (!p.name || !p.timezone) throw new Error("Case name and timezone are required.");
    const created = await apiRequest("/api/v1/cases", { ...api(), method: "POST", body: p });
    setSettings(prev => ({ ...prev, caseId: created.id }));
    setCreateCaseForm(prev => ({ ...prev, name: "" }));
    await loadCases();
  }

  async function addCaseMember() {
    if (!settings.caseId) throw new Error("Select a case first.");
    const p = { externalSubject: inviteMemberForm.externalSubject.trim(), displayName: inviteMemberForm.displayName.trim(), role: inviteMemberForm.role };
    if (!p.externalSubject || !p.displayName) throw new Error("Subject and display name are required.");
    await apiRequest(`/api/v1/cases/${settings.caseId}/people`, { ...api(), method: "POST", body: p });
    setInviteMemberForm(prev => ({ ...prev, externalSubject: "", displayName: "" }));
    await loadMembers();
  }

  async function loadScheduleRule() {
    if (!settings.caseId) return;
    try {
      const d = await apiRequest(`/api/v1/cases/${settings.caseId}/schedule-rule`, api());
      setScheduleRule(d);
      setScheduleRuleForm({ anchorDate: d.anchorDate || "", parentAId: d.parentAId || "", parentBId: d.parentBId || "", pattern: d.metadata?.pattern || "AABBAAABBAABBBB" });
    } catch { setScheduleRule(null); }
  }

  async function saveScheduleRule() {
    if (!settings.caseId) throw new Error("Select a case first.");
    if (!scheduleRuleForm.parentAId || !scheduleRuleForm.parentBId) throw new Error("Both parents must be selected.");
    if (!scheduleRuleForm.anchorDate) throw new Error("Anchor date is required.");
    const d = await apiRequest(`/api/v1/cases/${settings.caseId}/schedule-rule`, {
      ...api(), method: "PUT",
      body: { type: "TWO_TWO_THREE", anchorDate: scheduleRuleForm.anchorDate, parentAId: scheduleRuleForm.parentAId, parentBId: scheduleRuleForm.parentBId, metadata: { anchorParent: "A", pattern: scheduleRuleForm.pattern || "AABBAAABBAABBBB" } }
    });
    setScheduleRule(d);
  }

  async function loadEvents(from, to) {
    if (!settings.caseId) return;
    setEvents(await apiRequest(`/api/v1/cases/${settings.caseId}/events?from=${from}&to=${to}`, api()));
  }

  async function loadSchedule(from, to) {
    if (!settings.caseId) return;
    try { setScheduleDays(await apiRequest(`/api/v1/cases/${settings.caseId}/schedule?from=${from}&to=${to}`, api())); }
    catch { setScheduleDays([]); }
  }

  async function loadSchoolDays(from, to) {
    if (!settings.caseId) return;
    setSchoolDays(await apiRequest(`/api/v1/cases/${settings.caseId}/school-calendar-days?from=${from}&to=${to}`, api()));
  }

  async function upsertSchoolDay(day) {
    setSchoolDays(await apiRequest(`/api/v1/cases/${settings.caseId}/school-calendar-days`, { ...api(), method: "POST", body: [{ date: day.date, dayType: day.dayType }] }));
  }

  async function bulkGenerateSchoolDays(form) {
    setSchoolDays(await apiRequest(`/api/v1/cases/${settings.caseId}/school-calendar-days/bulk`, { ...api(), method: "POST", body: form }));
  }

  async function refreshCalendar() {
    const from = toDateInput(startOfMonth(calendarMonth));
    const to = toDateInput(endOfMonth(calendarMonth));
    await loadMembers();
    await loadScheduleRule();
    await loadSchedule(from, to);
    await loadEvents(from, to);
    await loadSchoolDays(from, to);
  }

  async function submitEvent(form) {
    await apiRequest(`/api/v1/cases/${settings.caseId}/events`, { ...api(), method: "POST", body: form });
    await loadEvents(form.startDate, form.endDate);
  }

  async function solveSchedule(form) {
    await loadMembers();
    const payload = { baseVersionId: null, horizonStart: form.horizonStart, horizonEnd: form.horizonEnd };
    if (!form.baselineOnly) {
      payload.newEvent = { title: form.title, startDate: form.startDate, endDate: form.endDate, eventType: form.eventType, appliesTo: form.appliesTo, parentId: form.parentId, locked: form.locked, recurrenceRule: "", notes: form.notes };
    }
    const data = await apiRequest(`/api/v1/cases/${settings.caseId}/schedule/solve`, { ...api(), method: "POST", body: payload });
    setSolveOptions(data.options || []);
    setSolveRequest(payload);
    setSelectedOption(null);
  }

  async function proposeOption(optionId) {
    if (!solveRequest) throw new Error("Run solve first.");
    const proposal = await apiRequest(`/api/v1/cases/${settings.caseId}/schedule/proposals`, { ...api(), method: "POST", body: { optionId, reason: "Proposed via web app", solveRequest } });
    setSelectedOption(proposal.proposalId);
    if (proposal.status === "APPROVED") { await refreshCalendar(); return; }
    setError("Proposal created. Waiting for other parent to approve.");
    setStatus("Pending Approval");
  }

  async function loadIdentities() {
    if (!settings.token) { setIdentities([]); return; }
    try { setIdentities((await apiRequest("/api/v1/me/identities", api())) || []); }
    catch { setIdentities([]); }
  }

  async function linkIdentity() {
    const externalSubject = linkForm.externalSubject.trim();
    if (!externalSubject) throw new Error("The other login's subject is required.");
    await apiRequest("/api/v1/me/identities", { ...api(), method: "POST", body: { externalSubject, label: linkForm.label.trim() || null } });
    setLinkForm({ externalSubject: "", label: "" });
    await loadIdentities();
  }

  async function unlinkIdentity(identityId) {
    await apiRequest(`/api/v1/me/identities/${identityId}`, { ...api(), method: "DELETE" });
    await loadIdentities();
  }

  async function eventAction(eventId, action) {
    const path = action === "delete"
      ? `/api/v1/cases/${settings.caseId}/events/${eventId}`
      : `/api/v1/cases/${settings.caseId}/events/${eventId}/${action}`;
    await apiRequest(path, { ...api(), method: action === "delete" ? "DELETE" : "POST" });
    await loadEvents(toDateInput(startOfMonth(calendarMonth)), toDateInput(endOfMonth(calendarMonth)));
  }

  async function ruleChangeAction(action) {
    await apiRequest(`/api/v1/cases/${settings.caseId}/schedule-rule/${action}`, { ...api(), method: "POST" });
    await loadScheduleRule();
  }

  async function loadAudit() {
    if (!settings.caseId) return;
    setAuditEntries((await apiRequest(`/api/v1/cases/${settings.caseId}/audit`, api())) || []);
  }

  async function loadLedger() {
    if (!settings.caseId) return;
    setLedgerEntries((await apiRequest(`/api/v1/cases/${settings.caseId}/ledger`, api())) || []);
    setLedgerBalances((await apiRequest(`/api/v1/cases/${settings.caseId}/ledger/balance`, api())) || []);
  }

  async function loadProposals() {
    if (!settings.caseId) return;
    setProposals((await apiRequest(`/api/v1/cases/${settings.caseId}/schedule/proposals`, api())) || []);
  }

  async function approveProposal(proposalId) {
    await apiRequest(`/api/v1/cases/${settings.caseId}/schedule/proposals/${proposalId}/approve`, { ...api(), method: "POST", body: { comment: "" } });
    await loadProposals();
    await refreshCalendar();
  }

  async function rejectProposal(proposalId) {
    await apiRequest(`/api/v1/cases/${settings.caseId}/schedule/proposals/${proposalId}/reject`, { ...api(), method: "POST", body: { comment: "" } });
    await loadProposals();
  }

  // ── Derived data ──
  const monthGrid = useMemo(() => getMonthGrid(calendarMonth), [calendarMonth]);
  const scheduleMap = useMemo(() => {
    const m = new Map();
    scheduleDays.forEach(d => m.set(d.date, d));
    return m;
  }, [scheduleDays]);

  const pendingCount = useMemo(() => proposals.filter(p => p.status === "PENDING_APPROVAL").length, [proposals]);

  const previewProposal = useMemo(() => proposals.find(p => p.proposalId === previewProposalId), [previewProposalId, proposals]);
  const proposalChangeMap = useMemo(() => {
    const m = new Map();
    if (!previewProposal?.optionSnapshot?.changedDays) return m;
    previewProposal.optionSnapshot.changedDays.forEach(ch => m.set(ch.date, ch));
    return m;
  }, [previewProposal]);

  function parentClass(parentId) {
    if (!scheduleRule) return "";
    if (parentId === scheduleRule.parentAId) return "parent-a";
    if (parentId === scheduleRule.parentBId) return "parent-b";
    return "";
  }

  function personLabel(id) {
    if (!id) return "Unassigned";
    return memberMap.get(id) || `Parent ${shortId(id)}`;
  }

  const activeCaseName = useMemo(() => {
    const c = cases.find(c => c.id === settings.caseId);
    return c ? c.name : "";
  }, [cases, settings.caseId]);

  // ── Shared calendar renderer ──
  function renderCalendarGrid(grid, sMap, changeOverlay, month) {
    return (
      <>
        {DAY_NAMES.map(d => <div key={d} className="day-label">{d}</div>)}
        {grid.map(({ date, current }, idx) => {
          const iso = toDateInput(date);
          const assignment = sMap.get(iso);
          const change = changeOverlay ? changeOverlay.get(iso) : null;
          const effectiveParent = change ? change.toParentId : assignment?.assignedParentId;
          const label = effectiveParent ? personLabel(effectiveParent) : "\u2014";
          const isToday = iso === todayStr;
          const cls = [
            "day",
            current ? "" : "dim",
            isToday ? "today" : "",
            parentClass(effectiveParent),
            change ? "proposed-change" : ""
          ].filter(Boolean).join(" ");
          return (
            <div key={`${iso}-${idx}`} className={cls}>
              <div className="day-top">
                <span className="day-num">{date.getDate()}</span>
                {assignment?.lockedSourceEventId && <span className="lock-icon">LOCK</span>}
              </div>
              <div className="day-owner">{label}</div>
              {change && <div className="day-meta">was {personLabel(change.fromParentId)}</div>}
              {!change && assignment && <div className="day-meta">{assignment.derivedFrom === "BASELINE" ? "" : assignment.derivedFrom}</div>}
            </div>
          );
        })}
      </>
    );
  }

  // ── Render ──
  return (
    <div className="app">
      {/* ── Header ── */}
      <header className="app-header">
        <span className="brand">Custody Calendar</span>
        <div className="case-select">
          <select
            value={settings.caseId}
            onChange={e => setSettings(p => ({ ...p, caseId: e.target.value }))}
          >
            <option value="">Select case...</option>
            {cases.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
        </div>
        <div className="header-right">
          <span className="header-status">
            {error ? <span className="error">{error}</span> : status !== "Ready" && status !== "Idle" ? status : activeCaseName}
          </span>
          {clerkConfigured && (
            <>
              <SignedOut><SignInButton mode="modal"><button type="button">Sign In</button></SignInButton></SignedOut>
              <SignedIn><UserButton /></SignedIn>
            </>
          )}
          <button className={`gear-btn ${settingsOpen ? "open" : ""}`} onClick={() => setSettingsOpen(p => !p)} title="Settings">{"\u2699"}</button>
        </div>
      </header>

      {/* ── Settings Drawer ── */}
      {settingsOpen && (
        <div className="settings-drawer">
          <div className="section-title" style={{ borderTop: "none" }}>Connection</div>
          <Field label="API Base URL">
            <input value={settings.baseUrl} onChange={e => setSettings(p => ({ ...p, baseUrl: e.target.value }))} placeholder={`Auto: ${resolveBaseUrl(settings)}`} />
          </Field>
          {!clerkConfigured && (
            <Field label="Bearer Token">
              <input value={settings.token} onChange={e => setSettings(p => ({ ...p, token: e.target.value }))} placeholder="Paste JWT" />
            </Field>
          )}
          {members.length > 0 && (
            <Field label="Act As (testing)">
              <select value={settings.subjectOverride} onChange={e => setSettings(p => ({ ...p, subjectOverride: e.target.value }))}>
                <option value="">Myself ({claims?.sub ? shortId(claims.sub) : "\u2014"})</option>
                {members.map(m => <option key={m.personId} value={m.externalSubject}>{m.displayName}</option>)}
              </select>
            </Field>
          )}

          {settings.token && (
            <>
              <div className="section-title">Linked Logins</div>
              <Field label="Logins that act as me">
                <div className="stack">
                  {identities.length === 0 && <small>No person record yet. Create or join a case first.</small>}
                  {identities.map(idn => (
                    <span key={idn.id}>
                      {idn.label || "Login"}: {idn.externalSubject}
                      {idn.primary ? " (primary)" : idn.current ? " (this login)" : ""}
                      {!idn.primary && !idn.current && (
                        <button style={{ marginLeft: 8 }} onClick={() => withStatus("Removing login", () => unlinkIdentity(idn.id))}>Remove</button>
                      )}
                    </span>
                  ))}
                </div>
              </Field>
              <Field label="Link Another Login">
                <div className="stack">
                  <small>Have them sign in on their device, copy "My subject" from their Settings, and paste it here. They will act as you: same calendar, same approvals.</small>
                  <input value={linkForm.externalSubject} onChange={e => setLinkForm(p => ({ ...p, externalSubject: e.target.value }))} placeholder="Their Clerk subject (user_...)" />
                  <input value={linkForm.label} onChange={e => setLinkForm(p => ({ ...p, label: e.target.value }))} placeholder="Label (e.g. partner's name)" />
                  <button onClick={() => withStatus("Linking login", linkIdentity)}>Link Login</button>
                </div>
              </Field>
            </>
          )}

          <div className="section-title">Case Setup</div>
          <Field label="Create New Case">
            <div className="stack">
              <input value={createCaseForm.name} onChange={e => setCreateCaseForm(p => ({ ...p, name: e.target.value }))} placeholder="Case name" />
              <input value={createCaseForm.timezone} onChange={e => setCreateCaseForm(p => ({ ...p, timezone: e.target.value }))} placeholder="America/Denver" />
              <button onClick={() => withStatus("Creating case", createCase)}>Create</button>
            </div>
          </Field>
          <Field label="Add Parent">
            <div className="stack">
              {claims?.sub && <small>My subject: {claims.sub}</small>}
              <input value={inviteMemberForm.externalSubject} onChange={e => setInviteMemberForm(p => ({ ...p, externalSubject: e.target.value }))} placeholder="Other parent's Clerk subject" />
              <input value={inviteMemberForm.displayName} onChange={e => setInviteMemberForm(p => ({ ...p, displayName: e.target.value }))} placeholder="Display name" />
              <select value={inviteMemberForm.role} onChange={e => setInviteMemberForm(p => ({ ...p, role: e.target.value }))}>
                <option value="PARENT">Parent</option>
                <option value="ADMIN">Admin</option>
              </select>
              <button onClick={() => withStatus("Adding parent", addCaseMember)} disabled={!settings.caseId}>Add Parent</button>
            </div>
          </Field>
          {members.length > 0 && (
            <Field label="Members">
              <div className="stack">
                {members.map(m => (
                  <span key={m.personId}>{m.displayName} ({m.role})</span>
                ))}
              </div>
            </Field>
          )}

          {members.length >= 2 && (
            <>
              <div className="section-title">Schedule Rule</div>
              {scheduleRule?.pendingChange && (
                <Field label="Pending Rule Change">
                  <div className="stack">
                    <small>
                      Requested by {personLabel(scheduleRule.changeRequestedBy)}: anchor {scheduleRule.pendingChange.anchorDate},
                      pattern {scheduleRule.pendingChange.metadata?.pattern || "?"}
                    </small>
                    <div style={{ display: "flex", gap: 6 }}>
                      <button className="btn-approve" onClick={() => withStatus("Approving rule change", () => ruleChangeAction("approve"))}>Approve</button>
                      <button className="btn-reject" onClick={() => withStatus("Rejecting rule change", () => ruleChangeAction("reject"))}>Reject</button>
                    </div>
                  </div>
                </Field>
              )}
              <Field label="Anchor Date">
                <input type="date" value={scheduleRuleForm.anchorDate} onChange={e => setScheduleRuleForm(p => ({ ...p, anchorDate: e.target.value }))} />
                <small>Day 1 of the repeating pattern for Parent A</small>
              </Field>
              <Field label="Parent A">
                <select value={scheduleRuleForm.parentAId} onChange={e => setScheduleRuleForm(p => ({ ...p, parentAId: e.target.value }))}>
                  <option value="">Select...</option>
                  {members.map(m => <option key={m.personId} value={m.personId}>{m.displayName}</option>)}
                </select>
              </Field>
              <Field label="Parent B">
                <select value={scheduleRuleForm.parentBId} onChange={e => setScheduleRuleForm(p => ({ ...p, parentBId: e.target.value }))}>
                  <option value="">Select...</option>
                  {members.map(m => <option key={m.personId} value={m.personId}>{m.displayName}</option>)}
                </select>
              </Field>
              <Field label="Custody Pattern">
                <input value={scheduleRuleForm.pattern} onChange={e => setScheduleRuleForm(p => ({ ...p, pattern: e.target.value.toUpperCase() }))} placeholder="AABBAAABBAABBBB" />
                <small>A/B chars. Default 2-2-3: AABBAAABBAABBBB</small>
              </Field>
              <div>
                <button className="primary" onClick={() => withStatus("Saving rule", saveScheduleRule)}>
                  {scheduleRule ? "Update Rule" : "Set Rule"}
                </button>
              </div>
            </>
          )}
        </div>
      )}

      {/* ── Tabs ── */}
      <nav className="tabs">
        {TABS.map(tab => (
          <button key={tab} className={tab === activeTab ? "active" : ""} onClick={() => setActiveTab(tab)}>
            {tab}
            {tab === "Proposals" && pendingCount > 0 && <span className="badge">{pendingCount}</span>}
          </button>
        ))}
      </nav>

      {/* ── Calendar Tab ── */}
      {activeTab === "Calendar" && (
        <section>
          <div className="calendar-header">
            <h2>{fmtMonth(calendarMonth)}</h2>
            <div className="calendar-actions">
              <button onClick={() => setCalendarMonth(addDays(startOfMonth(calendarMonth), -1))}>Prev</button>
              <button onClick={() => setCalendarMonth(new Date())}>Today</button>
              <button onClick={() => setCalendarMonth(addDays(endOfMonth(calendarMonth), 1))}>Next</button>
              <button onClick={() => withStatus("Refreshing", refreshCalendar)}>Refresh</button>
            </div>
          </div>
          {scheduleRule && (
            <div className="calendar-legend">
              <div className="legend-item"><span className="legend-swatch parent-a" />{personLabel(scheduleRule.parentAId)}</div>
              <div className="legend-item"><span className="legend-swatch parent-b" />{personLabel(scheduleRule.parentBId)}</div>
            </div>
          )}
          <div className="calendar-grid">
            {renderCalendarGrid(monthGrid, scheduleMap, null, calendarMonth)}
          </div>
        </section>
      )}

      {/* ── Events Tab ── */}
      {activeTab === "Events" && (
        <section className="two-col">
          <div className="panel">
            <h2>Events</h2>
            <button onClick={() => withStatus("Loading events", () => loadEvents(toDateInput(startOfMonth(calendarMonth)), toDateInput(endOfMonth(calendarMonth))))}>Refresh</button>
            <div className="event-list">
              {events.length === 0 && <div className="empty">No events for this month.</div>}
              {events.map(ev => {
                const pending = ev.approvalStatus && ev.approvalStatus !== "ACTIVE";
                return (
                  <div className="event" key={ev.id}>
                    <div>
                      <strong>{ev.title}</strong>
                      {pending && <span className="status-badge pending" style={{ marginLeft: 8 }}>{ev.approvalStatus.replace(/_/g, " ")}</span>}
                      <br /><span style={{ fontSize: 12, color: "var(--muted)" }}>{ev.startDate} to {ev.endDate}</span>
                      <div style={{ marginTop: 6, display: "flex", gap: 6 }}>
                        {pending && <button className="btn-approve" onClick={() => withStatus("Approving event", () => eventAction(ev.id, "approve"))}>Approve</button>}
                        {pending && <button className="btn-reject" onClick={() => withStatus("Rejecting event", () => eventAction(ev.id, "reject"))}>Reject</button>}
                        {!pending && <button onClick={() => withStatus("Deleting event", () => eventAction(ev.id, "delete"))}>Delete</button>}
                      </div>
                    </div>
                    <div className="event-meta">
                      <span>{ev.eventType.replace(/_/g, " ")}</span>
                      <span>{ev.locked ? "Locked" : "Flexible"}</span>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
          <EventForm members={members} onSubmit={form => withStatus("Creating event", () => submitEvent(form))} />
        </section>
      )}

      {/* ── School Days Tab ── */}
      {activeTab === "School Days" && (
        <section className="two-col">
          <div className="panel">
            <h2>School Calendar</h2>
            <button onClick={() => withStatus("Loading", () => loadSchoolDays(toDateInput(startOfMonth(calendarMonth)), toDateInput(endOfMonth(calendarMonth))))}>Refresh</button>
            <div className="event-list">
              {schoolDays.length === 0 && <div className="empty">No school days for this range.</div>}
              {schoolDays.map(d => (
                <div className="event" key={d.date}><div><strong>{d.date}</strong> <span style={{ color: "var(--muted)" }}>{d.dayType}</span></div></div>
              ))}
            </div>
          </div>
          <div className="stacked-forms">
            <SchoolDayForm onSubmit={form => withStatus("Saving", () => upsertSchoolDay(form))} />
            <SchoolDayBulkForm onSubmit={form => withStatus("Generating", () => bulkGenerateSchoolDays(form))} />
          </div>
        </section>
      )}

      {/* ── Proposals Tab ── */}
      {activeTab === "Proposals" && (
        <section className="proposals-layout">
          <div className="panel">
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
              <h2 style={{ margin: 0 }}>Proposals</h2>
              <button onClick={() => withStatus("Refreshing", loadProposals)}>Refresh</button>
            </div>
            <div className="proposal-list">
              {proposals.length === 0 && <div className="empty">No proposals yet. Use the Solve tab to create one.</div>}
              {proposals.map(p => {
                const statusClass = p.status === "PENDING_APPROVAL" ? "pending" : p.status === "APPROVED" ? "approved" : "rejected";
                const changeCount = p.optionSnapshot?.changedDays?.length || 0;
                return (
                  <div key={p.proposalId} className={`proposal-card ${previewProposalId === p.proposalId ? "selected" : ""}`} onClick={() => setPreviewProposalId(p.proposalId)}>
                    <div className="proposal-header">
                      <span className={`status-badge ${statusClass}`}>{p.status.replace(/_/g, " ")}</span>
                      <span className="proposal-by">{relativeTime(p.createdAt)}</span>
                    </div>
                    <div className="proposal-by">by {p.createdByName || shortId(p.createdBy)}</div>
                    {p.reason && <div className="proposal-reason">{p.reason}</div>}
                    <div className="proposal-changes">{changeCount} day{changeCount !== 1 ? "s" : ""} changed</div>
                    <div className="proposal-approvals">
                      {(p.approvals || []).map(a => (
                        <span key={a.personId} className={`approval-chip ${a.decision ? a.decision.toLowerCase() : "pending"}`}>
                          {a.displayName || shortId(a.personId)} {a.decision === "APPROVE" ? "\u2713" : a.decision === "REJECT" ? "\u2717" : "\u2022\u2022\u2022"}
                        </span>
                      ))}
                    </div>
                    {p.status === "PENDING_APPROVAL" && (
                      <div className="proposal-actions">
                        <button className="btn-approve" onClick={e => { e.stopPropagation(); withStatus("Approving", () => approveProposal(p.proposalId)); }}>Approve</button>
                        <button className="btn-reject" onClick={e => { e.stopPropagation(); withStatus("Rejecting", () => rejectProposal(p.proposalId)); }}>Reject</button>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
          <div className="proposal-preview panel">
            {!previewProposal ? (
              <div className="empty" style={{ marginTop: 40 }}>Select a proposal to preview calendar changes.</div>
            ) : (
              <>
                <div className="preview-header">
                  <h3>Preview: Option {previewProposal.optionId}</h3>
                  <span className={`status-badge ${previewProposal.status === "PENDING_APPROVAL" ? "pending" : previewProposal.status === "APPROVED" ? "approved" : "rejected"}`}>
                    {previewProposal.status.replace(/_/g, " ")}
                  </span>
                </div>
                {previewProposal.optionSnapshot?.changedDays && (
                  <div className="change-summary">
                    {previewProposal.optionSnapshot.changedDays.slice(0, 8).map(ch => (
                      <span key={ch.date} className="chip">{ch.date}: {personLabel(ch.fromParentId)} &rarr; {personLabel(ch.toParentId)}</span>
                    ))}
                    {previewProposal.optionSnapshot.changedDays.length > 8 && <span className="chip">+{previewProposal.optionSnapshot.changedDays.length - 8} more</span>}
                  </div>
                )}
                {scheduleRule && (
                  <div className="calendar-legend">
                    <div className="legend-item"><span className="legend-swatch parent-a" />{personLabel(scheduleRule.parentAId)}</div>
                    <div className="legend-item"><span className="legend-swatch parent-b" />{personLabel(scheduleRule.parentBId)}</div>
                  </div>
                )}
                <div className="calendar-grid">
                  {renderCalendarGrid(monthGrid, scheduleMap, proposalChangeMap, calendarMonth)}
                </div>
              </>
            )}
          </div>
        </section>
      )}

      {/* ── Solve Tab ── */}
      {activeTab === "Solve" && (
        <section className="two-col">
          <SolveForm members={members} onSolve={form => withStatus("Solving", () => solveSchedule(form))} />
          <div className="panel options">
            <h2>Options</h2>
            {solveOptions.length === 0 && <div className="empty">Run the solver to see schedule options.</div>}
            {solveOptions.map(option => (
              <div className="option" key={option.optionId}>
                <div className="option-header">
                  <h3>Option {option.optionId}</h3>
                  <span className="score">Score {option.scoreTotal}</span>
                </div>
                <div className="option-grid">
                  <div>
                    <h4>How it rates</h4>
                    <ul>
                      {Object.entries(option.scoreDetails || {}).map(([key, d]) => (
                        <li key={key}><span>{scoreLabel(key)} ({d.count} &times; {d.weight})</span><span>{d.score}</span></li>
                      ))}
                      {(!option.scoreDetails || Object.keys(option.scoreDetails).length === 0) && Object.entries(option.scoreBreakdown || {}).map(([k, v]) => (
                        <li key={k}><span>{scoreLabel(k)}</span><span>{v}</span></li>
                      ))}
                    </ul>
                  </div>
                  <div>
                    <h4>Days that change</h4>
                    <div className="chip-list">
                      {(option.changedDays || []).slice(0, 10).map(ch => (
                        <span key={ch.date} className="chip">{ch.date} {personLabel(ch.fromParentId)} &rarr; {personLabel(ch.toParentId)}</span>
                      ))}
                    </div>
                  </div>
                  <div>
                    <h4>Balance after</h4>
                    <div className="stack">
                      {(option.owedBalances || []).length === 0 && <span>Even</span>}
                      {(option.owedBalances || []).map((b, i) => (
                        <span key={i}>{personLabel(b.fromParentId)} owes {personLabel(b.toParentId)} {b.amountDays} {fmtBucket(b.dayBucket)} day{b.amountDays === 1 ? "" : "s"}</span>
                      ))}
                    </div>
                  </div>
                </div>
                <div className="option-actions">
                  <button className={selectedOption === option.optionId ? "primary" : ""} onClick={() => withStatus("Sending for approval", () => proposeOption(option.optionId))}>
                    {selectedOption === option.optionId ? "Sent" : "Send for Approval"}
                  </button>
                </div>
              </div>
            ))}
          </div>
        </section>
      )}
      {/* ── Activity Tab ── */}
      {activeTab === "Activity" && (
        <section>
          <div className="panel">
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
              <h2 style={{ margin: 0 }}>Activity</h2>
              <button onClick={() => withStatus("Refreshing activity", loadAudit)}>Refresh</button>
            </div>
            <div className="event-list">
              {auditEntries.length === 0 && <div className="empty">No activity recorded yet.</div>}
              {auditEntries.map(entry => (
                <div className="event" key={entry.id}>
                  <div>
                    <strong>{(entry.action || "").replace(/_/g, " ")}</strong>
                    {entry.details?.title && <span> — {entry.details.title}</span>}
                    {entry.details?.startDate && <span style={{ color: "var(--muted)" }}> ({entry.details.startDate}{entry.details.endDate && entry.details.endDate !== entry.details.startDate ? ` to ${entry.details.endDate}` : ""})</span>}
                    <div style={{ fontSize: 12, color: "var(--muted)" }}>
                      by {entry.actorName || personLabel(entry.actorPersonId)} · {relativeTime(entry.createdAt)}
                    </div>
                  </div>
                  <div className="event-meta"><span>{entry.entityType}</span></div>
                </div>
              ))}
            </div>
          </div>
        </section>
      )}

      {/* ── Ledger Tab ── */}
      {activeTab === "Ledger" && (
        <section className="two-col">
          <div className="panel">
            <h2>Current Balance</h2>
            <div className="stack">
              {ledgerBalances.length === 0 && <div className="empty">Even. Nobody owes any days.</div>}
              {ledgerBalances.map((b, i) => (
                <div className="event" key={i}>
                  <div>
                    <strong>{personLabel(b.fromParentId)}</strong> owes <strong>{personLabel(b.toParentId)}</strong>{" "}
                    {b.amountDays} {fmtBucket(b.dayBucket)} day{b.amountDays === 1 ? "" : "s"}
                  </div>
                </div>
              ))}
            </div>
            <p style={{ fontSize: 12, color: "var(--muted)" }}>
              Balances are netted from every entry below. Entries are recorded automatically when an approved
              proposal changes who has the kids relative to the baseline schedule.
            </p>
          </div>
          <div className="panel">
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
              <h2 style={{ margin: 0 }}>History</h2>
              <button onClick={() => withStatus("Refreshing ledger", loadLedger)}>Refresh</button>
            </div>
            <div className="event-list">
              {ledgerEntries.length === 0 && <div className="empty">No ledger entries yet.</div>}
              {ledgerEntries.map(entry => (
                <div className="event" key={entry.id}>
                  <div>
                    <strong>{entry.date}</strong>{" "}
                    {personLabel(entry.fromParentId)} &rarr; {personLabel(entry.toParentId)}:{" "}
                    {entry.amountDays} {fmtBucket(entry.dayBucket)} day{entry.amountDays === 1 ? "" : "s"}
                    {entry.notes && <div style={{ fontSize: 12, color: "var(--muted)" }}>{entry.notes}</div>}
                  </div>
                  <div className="event-meta">
                    <span>{(entry.reasonType || "").replace(/_/g, " ")}</span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </section>
      )}
    </div>
  );
}

// ── Sub-components ──

function EventForm({ members, onSubmit }) {
  const [form, setForm] = useState({
    title: "", startDate: toDateInput(new Date()), endDate: toDateInput(addDays(new Date(), 2)),
    eventType: "VACATION_WITH_KIDS", appliesTo: "KIDS_ASSIGNMENT", parentId: "", locked: false, recurrenceRule: "", notes: ""
  });
  return (
    <div className="panel">
      <h2>Add Event</h2>
      <div className="form-grid">
        <Field label="Title"><input value={form.title} onChange={e => setForm({ ...form, title: e.target.value })} placeholder="e.g. Spring Break" /></Field>
        <Field label="Start"><input type="date" value={form.startDate} onChange={e => setForm({ ...form, startDate: e.target.value })} /></Field>
        <Field label="End"><input type="date" value={form.endDate} onChange={e => setForm({ ...form, endDate: e.target.value })} /></Field>
        <Field label="Type">
          <select value={form.eventType} onChange={e => setForm({ ...form, eventType: e.target.value })}>
            <option value="VACATION_WITH_KIDS">Vacation with kids</option>
            <option value="VACATION_NO_KIDS">Vacation (no kids)</option>
            <option value="HOLIDAY_LOCKED">Holiday (locked)</option>
            <option value="EXCEPTION_SWAP">Exception swap</option>
          </select>
        </Field>
        <Field label="Which Parent">
          <select value={form.parentId} onChange={e => setForm({ ...form, parentId: e.target.value })}>
            <option value="">Select...</option>
            {(members || []).map(m => <option key={m.personId} value={m.personId}>{m.displayName}</option>)}
          </select>
        </Field>
        <Field label="Locked">
          <label className="toggle"><input type="checkbox" checked={form.locked} onChange={e => setForm({ ...form, locked: e.target.checked })} /><span>Lock these days</span></label>
        </Field>
        <Field label="Notes"><input value={form.notes} onChange={e => setForm({ ...form, notes: e.target.value })} placeholder="Optional" /></Field>
      </div>
      <button className="primary" onClick={() => onSubmit(form)}>Create Event</button>
    </div>
  );
}

function SolveForm({ members, onSolve }) {
  const [form, setForm] = useState({
    baselineOnly: false, title: "Vacation Request",
    startDate: toDateInput(addDays(new Date(), 10)), endDate: toDateInput(addDays(new Date(), 14)),
    horizonStart: toDateInput(startOfMonth(new Date())), horizonEnd: toDateInput(endOfMonth(new Date())),
    eventType: "VACATION_WITH_KIDS", appliesTo: "KIDS_ASSIGNMENT", parentId: "", locked: false, notes: ""
  });
  return (
    <div className="panel">
      <h2>Run Solver</h2>
      <div className="form-grid">
        <Field label="Just re-score current">
          <label className="toggle"><input type="checkbox" checked={form.baselineOnly} onChange={e => setForm({ ...form, baselineOnly: e.target.checked })} /><span>Baseline only</span></label>
        </Field>
        <Field label="What's happening?"><input value={form.title} onChange={e => setForm({ ...form, title: e.target.value })} disabled={form.baselineOnly} /></Field>
        <Field label="Event from"><input type="date" value={form.startDate} onChange={e => setForm({ ...form, startDate: e.target.value })} disabled={form.baselineOnly} /></Field>
        <Field label="Event to"><input type="date" value={form.endDate} onChange={e => setForm({ ...form, endDate: e.target.value })} disabled={form.baselineOnly} /></Field>
        <Field label="Schedule from"><input type="date" value={form.horizonStart} onChange={e => setForm({ ...form, horizonStart: e.target.value })} /></Field>
        <Field label="Schedule to"><input type="date" value={form.horizonEnd} onChange={e => setForm({ ...form, horizonEnd: e.target.value })} /></Field>
        <Field label="Which parent?">
          <select value={form.parentId} onChange={e => setForm({ ...form, parentId: e.target.value })} disabled={form.baselineOnly}>
            <option value="">Select...</option>
            {members.map(m => <option key={m.personId} value={m.personId}>{m.displayName}</option>)}
          </select>
        </Field>
        <Field label="Type">
          <select value={form.eventType} onChange={e => setForm({ ...form, eventType: e.target.value })} disabled={form.baselineOnly}>
            <option value="VACATION_WITH_KIDS">Vacation with kids</option>
            <option value="VACATION_NO_KIDS">Vacation (no kids)</option>
            <option value="HOLIDAY_LOCKED">Holiday (locked)</option>
            <option value="EXCEPTION_SWAP">Exception swap</option>
          </select>
        </Field>
        <Field label="Lock days">
          <label className="toggle"><input type="checkbox" checked={form.locked} onChange={e => setForm({ ...form, locked: e.target.checked })} disabled={form.baselineOnly} /><span>Must keep these days</span></label>
        </Field>
      </div>
      <button className="primary" onClick={() => onSolve(form)}>Solve</button>
    </div>
  );
}

function SchoolDayForm({ onSubmit }) {
  const [form, setForm] = useState({ date: toDateInput(new Date()), dayType: "SCHOOL" });
  return (
    <div className="panel">
      <h2>Add School Day</h2>
      <div className="form-grid">
        <Field label="Date"><input type="date" value={form.date} onChange={e => setForm({ ...form, date: e.target.value })} /></Field>
        <Field label="Day Type">
          <select value={form.dayType} onChange={e => setForm({ ...form, dayType: e.target.value })}>
            <option value="SCHOOL">School</option><option value="BREAK">Break</option><option value="WEEKEND">Weekend</option><option value="HOLIDAY">Holiday</option><option value="IN_SERVICE">In Service</option>
          </select>
        </Field>
      </div>
      <button className="primary" onClick={() => onSubmit(form)}>Save Day</button>
    </div>
  );
}

function SchoolDayBulkForm({ onSubmit }) {
  const [form, setForm] = useState({ startDate: "2025-08-04", endDate: "2026-05-25", dayType: "SCHOOL", daysOfWeek: ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY"] });
  function toggleDay(day) {
    const set = new Set(form.daysOfWeek);
    set.has(day) ? set.delete(day) : set.add(day);
    setForm({ ...form, daysOfWeek: Array.from(set) });
  }
  return (
    <div className="panel">
      <h2>Bulk Generate</h2>
      <div className="form-grid">
        <Field label="Start"><input type="date" value={form.startDate} onChange={e => setForm({ ...form, startDate: e.target.value })} /></Field>
        <Field label="End"><input type="date" value={form.endDate} onChange={e => setForm({ ...form, endDate: e.target.value })} /></Field>
        <Field label="Type">
          <select value={form.dayType} onChange={e => setForm({ ...form, dayType: e.target.value })}>
            <option value="SCHOOL">School</option><option value="BREAK">Break</option><option value="WEEKEND">Weekend</option><option value="HOLIDAY">Holiday</option><option value="IN_SERVICE">In Service</option>
          </select>
        </Field>
        <Field label="Days">
          <div className="day-toggle-grid">
            {["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"].map(d => (
              <label key={d} className={`day-toggle ${form.daysOfWeek.includes(d) ? "active" : ""}`}>
                <input type="checkbox" checked={form.daysOfWeek.includes(d)} onChange={() => toggleDay(d)} /><span>{d.slice(0,3)}</span>
              </label>
            ))}
          </div>
        </Field>
      </div>
      <button className="primary" onClick={() => onSubmit(form)}>Generate</button>
    </div>
  );
}

function Field({ label, children }) {
  return <div className="field"><label>{label}</label>{children}</div>;
}
