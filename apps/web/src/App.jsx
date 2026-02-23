import React, { useEffect, useMemo, useState } from "react";
import { apiRequest } from "./api.js";

const TABS = ["Calendar", "Events", "School Days", "Solve"];

const defaultSettings = {
  baseUrl: "http://localhost:8080",
  token: "",
  caseId: ""
};

function loadStoredSettings() {
  try {
    const stored = JSON.parse(localStorage.getItem("cc-settings"));
    return { ...defaultSettings, ...stored };
  } catch {
    return defaultSettings;
  }
}

function persistSettings(settings) {
  localStorage.setItem("cc-settings", JSON.stringify(settings));
}

function toDateInput(date) {
  return date.toISOString().slice(0, 10);
}

function startOfMonth(date) {
  return new Date(date.getFullYear(), date.getMonth(), 1);
}

function endOfMonth(date) {
  return new Date(date.getFullYear(), date.getMonth() + 1, 0);
}

function addDays(date, count) {
  const result = new Date(date);
  result.setDate(result.getDate() + count);
  return result;
}

function formatMonthTitle(date) {
  return date.toLocaleDateString("en-US", { month: "long", year: "numeric" });
}

function shortId(value) {
  return value ? String(value).slice(0, 6) : "";
}

function labelScoreKey(key) {
  const labels = {
    transitions: "Transitions",
    schoolNightTransitions: "School-night transitions",
    parityDrift: "Weekend parity drift",
    lockedProximity: "Changes near locked days",
    owedImbalance: "Total owed days",
    runDaysOverThree: "Run days over 3"
  };
  return labels[key] || key;
}

function formatDayBucket(bucket) {
  if (bucket === "SCHOOL") return "school";
  if (bucket === "NON_SCHOOL") return "non-school";
  return (bucket || "").toLowerCase();
}

function getMonthGrid(date) {
  const start = startOfMonth(date);
  const end = endOfMonth(date);
  const startOffset = (start.getDay() + 6) % 7;
  const days = [];
  for (let i = 0; i < startOffset; i += 1) {
    days.push({ date: addDays(start, i - startOffset), current: false });
  }
  for (let d = 0; d < end.getDate(); d += 1) {
    days.push({ date: addDays(start, d), current: true });
  }
  while (days.length % 7 !== 0) {
    days.push({ date: addDays(end, days.length - (startOffset + end.getDate()) + 1), current: false });
  }
  return days;
}

export default function App() {
  const [settings, setSettings] = useState(loadStoredSettings);
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

  const memberMap = useMemo(() => {
    const map = new Map();
    members.forEach(member => {
      map.set(member.personId, member.displayName);
    });
    return map;
  }, [members]);

  useEffect(() => {
    persistSettings(settings);
  }, [settings]);

  async function withStatus(label, fn) {
    setStatus(label);
    setError("");
    try {
      await fn();
      setStatus("Ready");
    } catch (err) {
      setError(err.message || String(err));
      setStatus("Error");
    }
  }

  async function loadMembers() {
    if (!settings.caseId) {
      return;
    }
    const data = await apiRequest(`/api/v1/cases/${settings.caseId}/people`, settings);
    setMembers(data);
  }

  async function loadEvents(rangeStart, rangeEnd) {
    if (!settings.caseId) {
      return;
    }
    const data = await apiRequest(
      `/api/v1/cases/${settings.caseId}/events?from=${rangeStart}&to=${rangeEnd}`,
      settings
    );
    setEvents(data);
  }

  async function loadSchedule(rangeStart, rangeEnd) {
    if (!settings.caseId) {
      return;
    }
    const data = await apiRequest(
      `/api/v1/cases/${settings.caseId}/schedule?from=${rangeStart}&to=${rangeEnd}`,
      settings
    );
    setScheduleDays(data);
  }

  async function loadSchoolDays(rangeStart, rangeEnd) {
    if (!settings.caseId) {
      return;
    }
    const data = await apiRequest(
      `/api/v1/cases/${settings.caseId}/school-calendar-days?from=${rangeStart}&to=${rangeEnd}`,
      settings
    );
    setSchoolDays(data);
  }

  async function upsertSchoolDay(day) {
    const payload = [
      {
        date: day.date,
        dayType: day.dayType
      }
    ];
    const data = await apiRequest(`/api/v1/cases/${settings.caseId}/school-calendar-days`, {
      ...settings,
      method: "POST",
      body: payload
    });
    setSchoolDays(data);
  }

  async function bulkGenerateSchoolDays(form) {
    const payload = {
      startDate: form.startDate,
      endDate: form.endDate,
      dayType: form.dayType,
      daysOfWeek: form.daysOfWeek
    };
    const data = await apiRequest(`/api/v1/cases/${settings.caseId}/school-calendar-days/bulk`, {
      ...settings,
      method: "POST",
      body: payload
    });
    setSchoolDays(data);
  }

  async function refreshCalendar() {
    const from = toDateInput(startOfMonth(calendarMonth));
    const to = toDateInput(endOfMonth(calendarMonth));
    await loadMembers();
    await loadSchedule(from, to);
    await loadEvents(from, to);
    await loadSchoolDays(from, to);
  }

  useEffect(() => {
    if (activeTab === "Calendar" && settings.caseId) {
      withStatus("Loading calendar", refreshCalendar);
    }
  }, [activeTab, calendarMonth, settings.caseId]);

  useEffect(() => {
    if (activeTab === "Solve" && settings.caseId) {
      withStatus("Loading members", loadMembers);
    }
  }, [activeTab, settings.caseId]);

  async function submitEvent(form) {
    await apiRequest(`/api/v1/cases/${settings.caseId}/events`, {
      ...settings,
      method: "POST",
      body: form
    });
    await loadEvents(form.startDate, form.endDate);
  }

  async function solveSchedule(form) {
    await loadMembers();
    const payload = {
      baseVersionId: null,
      horizonStart: form.horizonStart,
      horizonEnd: form.horizonEnd
    };
    if (!form.baselineOnly) {
      payload.newEvent = {
        title: form.title,
        startDate: form.startDate,
        endDate: form.endDate,
        eventType: form.eventType,
        appliesTo: form.appliesTo,
        parentId: form.parentId,
        locked: form.locked,
        recurrenceRule: "",
        notes: form.notes
      };
    }
    const data = await apiRequest(`/api/v1/cases/${settings.caseId}/schedule/solve`, {
      ...settings,
      method: "POST",
      body: payload
    });
    setSolveOptions(data.options || []);
    setSolveRequest(payload);
    setSelectedOption(null);
  }

  async function acceptOption(optionId) {
    if (!solveRequest) {
      throw new Error("Solve request not available. Run solve first.");
    }
    const response = await apiRequest(`/api/v1/cases/${settings.caseId}/schedule/solve/accept`, {
      ...settings,
      method: "POST",
      body: {
        optionId,
        reason: "Accepted via web MVP",
        solveRequest
      }
    });
    setSelectedOption(response.optionId);
    await refreshCalendar();
    return response;
  }

  const monthGrid = useMemo(() => getMonthGrid(calendarMonth), [calendarMonth]);
  const scheduleMap = useMemo(() => {
    const map = new Map();
    scheduleDays.forEach(day => {
      map.set(day.date, day);
    });
    return map;
  }, [scheduleDays]);

  function personLabel(personId) {
    if (!personId) {
      return "Unknown";
    }
    return memberMap.get(personId) || `Parent ${shortId(personId)}`;
  }

  return (
    <div className="app">
      <header className="hero">
        <div>
          <p className="eyebrow">Custody Calendar</p>
          <h1>Balance changes without breaking the baseline.</h1>
          <p className="subhead">
            Deterministic solver options with transparent scoring. Use the controls below to connect your case and plan.
          </p>
        </div>
        <div className="card settings">
          <div className="field">
            <label>API Base URL</label>
            <input
              value={settings.baseUrl}
              onChange={event => setSettings({ ...settings, baseUrl: event.target.value })}
              placeholder="http://localhost:8080"
            />
          </div>
          <div className="field">
            <label>Bearer Token</label>
            <input
              value={settings.token}
              onChange={event => setSettings({ ...settings, token: event.target.value })}
              placeholder="paste JWT"
            />
          </div>
          <div className="field">
            <label>Case ID</label>
            <input
              value={settings.caseId}
              onChange={event => setSettings({ ...settings, caseId: event.target.value })}
              placeholder="case UUID"
            />
          </div>
          <div className="status">
            <span>{status}</span>
            {error && <span className="error">{error}</span>}
          </div>
        </div>
      </header>

      <nav className="tabs">
        {TABS.map(tab => (
          <button
            key={tab}
            className={tab === activeTab ? "active" : ""}
            onClick={() => setActiveTab(tab)}
          >
            {tab}
          </button>
        ))}
      </nav>

      {activeTab === "Calendar" && (
        <section className="calendar">
          <div className="calendar-header">
            <div>
              <h2>{formatMonthTitle(calendarMonth)}</h2>
              <p>Accepted schedule assignments for this month.</p>
            </div>
            <div className="calendar-actions">
              <button onClick={() => setCalendarMonth(addDays(startOfMonth(calendarMonth), -1))}>Prev</button>
              <button onClick={() => setCalendarMonth(new Date())}>Today</button>
              <button onClick={() => setCalendarMonth(addDays(endOfMonth(calendarMonth), 1))}>Next</button>
              <button onClick={() => withStatus("Refreshing", refreshCalendar)}>Refresh</button>
            </div>
          </div>
          <div className="calendar-grid">
            {monthGrid.map(({ date, current }, idx) => {
              const iso = toDateInput(date);
              const assignment = scheduleMap.get(iso);
              const label = assignment
                ? memberMap.get(assignment.assignedParentId) || assignment.assignedParentId.slice(0, 6)
                : "—";
              return (
                <div key={`${iso}-${idx}`} className={`day ${current ? "" : "dim"}`}>
                  <div className="day-top">
                    <span>{date.getDate()}</span>
                    {assignment?.lockedSourceEventId && <span className="lock">Locked</span>}
                  </div>
                  <div className="day-owner">{label}</div>
                  <div className="day-meta">{assignment?.derivedFrom || "UNASSIGNED"}</div>
                </div>
              );
            })}
          </div>
        </section>
      )}

      {activeTab === "Events" && (
        <section className="events">
          <div className="panel">
            <h2>Event Log</h2>
            <p>Events within the current month view.</p>
            <button onClick={() => withStatus("Loading events", () => loadEvents(
              toDateInput(startOfMonth(calendarMonth)),
              toDateInput(endOfMonth(calendarMonth))
            ))}>Refresh</button>
            <div className="event-list">
              {events.length === 0 && <div className="empty">No events for this range.</div>}
              {events.map(event => (
                <div className="event" key={event.id}>
                  <div>
                    <strong>{event.title}</strong>
                    <span>{event.startDate} to {event.endDate}</span>
                  </div>
                  <div className="event-meta">
                    <span>{event.eventType}</span>
                    <span>{event.appliesTo}</span>
                    <span>{event.locked ? "Locked" : "Flexible"}</span>
                  </div>
                </div>
              ))}
            </div>
          </div>
          <EventForm onSubmit={form => withStatus("Creating event", () => submitEvent(form))} />
        </section>
      )}

      {activeTab === "School Days" && (
        <section className="events">
          <div className="panel">
            <h2>School Calendar</h2>
            <p>Define which days are school, break, or holiday for scoring.</p>
            <button onClick={() => withStatus("Loading school days", () => loadSchoolDays(
              toDateInput(startOfMonth(calendarMonth)),
              toDateInput(endOfMonth(calendarMonth))
            ))}>Refresh</button>
            <div className="event-list">
              {schoolDays.length === 0 && <div className="empty">No school calendar days for this range.</div>}
              {schoolDays.map(day => (
                <div className="event" key={day.date}>
                  <div>
                    <strong>{day.date}</strong>
                    <span>{day.dayType}</span>
                  </div>
                </div>
              ))}
            </div>
          </div>
          <div className="stacked-forms">
            <SchoolDayForm onSubmit={form => withStatus("Saving school day", () => upsertSchoolDay(form))} />
            <SchoolDayBulkForm onSubmit={form => withStatus("Generating school days", () => bulkGenerateSchoolDays(form))} />
          </div>
        </section>
      )}

      {activeTab === "Solve" && (
        <section className="solve">
          <SolveForm
            members={members}
            onSolve={form => withStatus("Solving schedule", () => solveSchedule(form))}
          />
          <div className="panel options">
            <h2>Solver Options</h2>
            {solveOptions.length === 0 && <div className="empty">Run solve to see options.</div>}
            {solveOptions.map(option => (
              <div className="option" key={option.optionId}>
                <div className="option-header">
                  <h3>Option {option.optionId}</h3>
                  <span className="score">Score {option.scoreTotal}</span>
                </div>
                <div className="option-grid">
                  <div>
                    <h4>Score Breakdown</h4>
                    <ul>
                      {Object.entries(option.scoreDetails || {}).map(([key, detail]) => (
                        <li key={key}>
                          <span>{labelScoreKey(key)} ({detail.count} x {detail.weight})</span>
                          <span>{detail.score}</span>
                        </li>
                      ))}
                      {(!option.scoreDetails || Object.keys(option.scoreDetails).length === 0) &&
                        Object.entries(option.scoreBreakdown || {}).map(([key, value]) => (
                          <li key={key}>
                            <span>{labelScoreKey(key)}</span>
                            <span>{value}</span>
                          </li>
                        ))}
                    </ul>
                  </div>
                  <div>
                    <h4>Changed Days</h4>
                    <div className="chip-list">
                      {(option.changedDays || []).slice(0, 10).map(change => (
                        <span key={change.date} className="chip">
                          {change.date} {personLabel(change.fromParent)} -> {personLabel(change.toParent)}
                        </span>
                      ))}
                    </div>
                  </div>
                  <div>
                    <h4>Owed Balances</h4>
                    <div className="stack">
                      {(option.owedBalances || []).length === 0 && <span>None</span>}
                      {(option.owedBalances || []).map((balance, idx) => (
                        <span key={`${option.optionId}-owed-${idx}`}>
                          {personLabel(balance.fromParent)} owes {personLabel(balance.toParent)}{" "}
                          {balance.amountDays} {formatDayBucket(balance.dayBucket)} day{balance.amountDays === 1 ? "" : "s"}
                        </span>
                      ))}
                    </div>
                    <h4>Patch Operations</h4>
                    <div className="stack">
                      {(option.patchOperations || []).map((op, idx) => (
                        <span key={`${option.optionId}-${idx}`}>{op}</span>
                      ))}
                      {(option.ledgerImpact || []).map((impact, idx) => (
                        <span key={`${option.optionId}-ledger-${idx}`}>
                          Ledger: {personLabel(impact.fromParent)} owes {personLabel(impact.toParent)}{" "}
                          {impact.amountDays} {formatDayBucket(impact.dayBucket)} day{impact.amountDays === 1 ? "" : "s"}
                        </span>
                      ))}
                    </div>
                  </div>
                </div>
                <div className="option-actions">
                  <button
                    className={selectedOption === option.optionId ? "primary" : ""}
                    onClick={() => withStatus("Accepting option", () => acceptOption(option.optionId))}
                  >
                    {selectedOption === option.optionId ? "Accepted" : "Accept Option"}
                  </button>
                </div>
              </div>
            ))}
          </div>
        </section>
      )}
    </div>
  );
}

function EventForm({ onSubmit }) {
  const [form, setForm] = useState({
    title: "School Break",
    startDate: toDateInput(new Date()),
    endDate: toDateInput(addDays(new Date(), 2)),
    eventType: "VACATION_WITH_KIDS",
    appliesTo: "KIDS_ASSIGNMENT",
    parentId: "",
    locked: false,
    recurrenceRule: "",
    notes: ""
  });

  return (
    <div className="panel form">
      <h2>Add Event</h2>
      <div className="form-grid">
        <Field label="Title">
          <input value={form.title} onChange={event => setForm({ ...form, title: event.target.value })} />
        </Field>
        <Field label="Start Date">
          <input type="date" value={form.startDate} onChange={event => setForm({ ...form, startDate: event.target.value })} />
        </Field>
        <Field label="End Date">
          <input type="date" value={form.endDate} onChange={event => setForm({ ...form, endDate: event.target.value })} />
        </Field>
        <Field label="Event Type">
          <select value={form.eventType} onChange={event => setForm({ ...form, eventType: event.target.value })}>
            <option value="VACATION_WITH_KIDS">Vacation With Kids</option>
            <option value="VACATION_NO_KIDS">Vacation No Kids</option>
            <option value="HOLIDAY_LOCKED">Holiday Locked</option>
            <option value="EXCEPTION_SWAP">Exception Swap</option>
          </select>
        </Field>
        <Field label="Applies To">
          <select value={form.appliesTo} onChange={event => setForm({ ...form, appliesTo: event.target.value })}>
            <option value="KIDS_ASSIGNMENT">Kids Assignment</option>
            <option value="PARENT_UNAVAILABLE">Parent Unavailable</option>
          </select>
        </Field>
        <Field label="Parent ID">
          <input value={form.parentId} onChange={event => setForm({ ...form, parentId: event.target.value })} />
        </Field>
        <Field label="Locked">
          <label className="toggle">
            <input
              type="checkbox"
              checked={form.locked}
              onChange={event => setForm({ ...form, locked: event.target.checked })}
            />
            <span>Lock event</span>
          </label>
        </Field>
        <Field label="Notes">
          <input value={form.notes} onChange={event => setForm({ ...form, notes: event.target.value })} />
        </Field>
      </div>
      <button className="primary" onClick={() => onSubmit(form)}>Create Event</button>
    </div>
  );
}

function SolveForm({ members, onSolve }) {
  const [form, setForm] = useState({
    baselineOnly: false,
    title: "Vacation Request",
    startDate: toDateInput(addDays(new Date(), 10)),
    endDate: toDateInput(addDays(new Date(), 14)),
    horizonStart: toDateInput(startOfMonth(new Date())),
    horizonEnd: toDateInput(endOfMonth(new Date())),
    eventType: "VACATION_WITH_KIDS",
    appliesTo: "KIDS_ASSIGNMENT",
    parentId: "",
    locked: false,
    notes: ""
  });

  return (
    <div className="panel form">
      <h2>Run Solver</h2>
      <div className="form-grid">
        <Field label="Baseline Only">
          <label className="toggle">
            <input
              type="checkbox"
              checked={form.baselineOnly}
              onChange={event => setForm({ ...form, baselineOnly: event.target.checked })}
            />
            <span>Skip event</span>
          </label>
        </Field>
        <Field label="Title">
          <input
            value={form.title}
            onChange={event => setForm({ ...form, title: event.target.value })}
            disabled={form.baselineOnly}
          />
        </Field>
        <Field label="Event Start">
          <input
            type="date"
            value={form.startDate}
            onChange={event => setForm({ ...form, startDate: event.target.value })}
            disabled={form.baselineOnly}
          />
        </Field>
        <Field label="Event End">
          <input
            type="date"
            value={form.endDate}
            onChange={event => setForm({ ...form, endDate: event.target.value })}
            disabled={form.baselineOnly}
          />
        </Field>
        <Field label="Horizon Start">
          <input type="date" value={form.horizonStart} onChange={event => setForm({ ...form, horizonStart: event.target.value })} />
        </Field>
        <Field label="Horizon End">
          <input type="date" value={form.horizonEnd} onChange={event => setForm({ ...form, horizonEnd: event.target.value })} />
        </Field>
        <Field label="Parent ID">
          <select
            value={form.parentId}
            onChange={event => setForm({ ...form, parentId: event.target.value })}
            disabled={form.baselineOnly}
          >
            <option value="">Select parent</option>
            {members.map(member => (
              <option key={member.personId} value={member.personId}>
                {member.displayName || member.personId}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Event Type">
          <select
            value={form.eventType}
            onChange={event => setForm({ ...form, eventType: event.target.value })}
            disabled={form.baselineOnly}
          >
            <option value="VACATION_WITH_KIDS">Vacation With Kids</option>
            <option value="VACATION_NO_KIDS">Vacation No Kids</option>
            <option value="HOLIDAY_LOCKED">Holiday Locked</option>
            <option value="EXCEPTION_SWAP">Exception Swap</option>
          </select>
        </Field>
        <Field label="Applies To">
          <select
            value={form.appliesTo}
            onChange={event => setForm({ ...form, appliesTo: event.target.value })}
            disabled={form.baselineOnly}
          >
            <option value="KIDS_ASSIGNMENT">Kids Assignment</option>
            <option value="PARENT_UNAVAILABLE">Parent Unavailable</option>
          </select>
        </Field>
        <Field label="Locked">
          <label className="toggle">
            <input
              type="checkbox"
              checked={form.locked}
              onChange={event => setForm({ ...form, locked: event.target.checked })}
              disabled={form.baselineOnly}
            />
            <span>Lock event</span>
          </label>
        </Field>
        <Field label="Notes">
          <input
            value={form.notes}
            onChange={event => setForm({ ...form, notes: event.target.value })}
            disabled={form.baselineOnly}
          />
        </Field>
      </div>
      <button className="primary" onClick={() => onSolve(form)}>Solve</button>
    </div>
  );
}

function SchoolDayForm({ onSubmit }) {
  const [form, setForm] = useState({
    date: toDateInput(new Date()),
    dayType: "SCHOOL"
  });

  return (
    <div className="panel form">
      <h2>Add School Day</h2>
      <div className="form-grid">
        <Field label="Date">
          <input
            type="date"
            value={form.date}
            onChange={event => setForm({ ...form, date: event.target.value })}
          />
        </Field>
        <Field label="Day Type">
          <select value={form.dayType} onChange={event => setForm({ ...form, dayType: event.target.value })}>
            <option value="SCHOOL">School</option>
            <option value="BREAK">Break</option>
            <option value="WEEKEND">Weekend</option>
            <option value="HOLIDAY">Holiday</option>
            <option value="IN_SERVICE">In Service</option>
          </select>
        </Field>
      </div>
      <button className="primary" onClick={() => onSubmit(form)}>Save Day</button>
    </div>
  );
}

function SchoolDayBulkForm({ onSubmit }) {
  const [form, setForm] = useState({
    startDate: "2025-08-04",
    endDate: "2026-05-25",
    dayType: "SCHOOL",
    daysOfWeek: ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY"]
  });

  function toggleDay(day) {
    const set = new Set(form.daysOfWeek);
    if (set.has(day)) {
      set.delete(day);
    } else {
      set.add(day);
    }
    setForm({ ...form, daysOfWeek: Array.from(set) });
  }

  const dayOptions = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"];

  return (
    <div className="panel form">
      <h2>Bulk Generate</h2>
      <div className="form-grid">
        <Field label="Start Date">
          <input
            type="date"
            value={form.startDate}
            onChange={event => setForm({ ...form, startDate: event.target.value })}
          />
        </Field>
        <Field label="End Date">
          <input
            type="date"
            value={form.endDate}
            onChange={event => setForm({ ...form, endDate: event.target.value })}
          />
        </Field>
        <Field label="Day Type">
          <select value={form.dayType} onChange={event => setForm({ ...form, dayType: event.target.value })}>
            <option value="SCHOOL">School</option>
            <option value="BREAK">Break</option>
            <option value="WEEKEND">Weekend</option>
            <option value="HOLIDAY">Holiday</option>
            <option value="IN_SERVICE">In Service</option>
          </select>
        </Field>
        <Field label="Days Of Week">
          <div className="day-toggle-grid">
            {dayOptions.map(day => (
              <label key={day} className={`day-toggle ${form.daysOfWeek.includes(day) ? "active" : ""}`}>
                <input
                  type="checkbox"
                  checked={form.daysOfWeek.includes(day)}
                  onChange={() => toggleDay(day)}
                />
                <span>{day.slice(0, 3)}</span>
              </label>
            ))}
          </div>
        </Field>
      </div>
      <button className="primary" onClick={() => onSubmit(form)}>Generate Days</button>
    </div>
  );
}

function Field({ label, children }) {
  return (
    <div className="field">
      <label>{label}</label>
      {children}
    </div>
  );
}
