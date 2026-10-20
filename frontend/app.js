/*
 * ResearchAI frontend (plain JavaScript, no framework).
 *
 * File layout:
 *   1. Data & API          - api() helper, loading data from the Spring Boot backend, backend status pill
 *   2. Page sections       - render functions for Overview, Projects, Datasets, Experiments, admin demo pages
 *   3. AI Assistant        - multiple conversations saved in localStorage, safe Markdown rendering
 *   4. Model training      - dataset preview, start a real training run, poll its status, show metrics, predict
 *   5. Page rendering      - render() switches between pages
 *   6. Create / edit modal - forms that save to the backend (or to localStorage for demo sections)
 *   7. Event listeners     - registered once, using event delegation
 *
 * All text from users or the AI is passed through esc() before being inserted as HTML.
 */

const $ = (s) => document.querySelector(s);
const $$ = (s) => [...document.querySelectorAll(s)];

/* ===================== Data & API ===================== */

// Backend address. Set in config.js (loaded before this file); falls back to the local backend.
const API_BASE = window.RESEARCHAI_API_BASE || "http://localhost:8080/api";

// Projects, datasets and training jobs come from MySQL through the Spring Boot API.
// The sections below are DEMO data kept only in this browser's localStorage.
const demoDefaults = {
    experiments: [
        { id: 1, name: "Baseline v1", model: "ResNet-18", metric: "91.2% accuracy", status: "Completed" },
        { id: 2, name: "Learning rate sweep", model: "ResNet-18", metric: "In progress", status: "Running" },
    ],
    collaboration: [
        { id: 1, project: "Computer Vision for Crop Health", members: "Keshav, Aditi", status: "Active" },
        { id: 2, project: "Efficient LLM Evaluation", members: "Research Group A", status: "Invited" },
    ],
    users: [
        { id: 1, name: "Keshav Tomar", email: "keshav@example.com", role: "Researcher", status: "Active" },
        { id: 2, name: "Aditi Sharma", email: "aditi@example.com", role: "Researcher", status: "Active" },
        { id: 3, name: "Platform Admin", email: "admin@example.com", role: "Admin", status: "Active" },
    ],
    resources: [
        {
            id: 1,
            name: "GPU Compute Node A10",
            type: "GPU instance",
            capacity: "1 GPU · 24 GB",
            usage: 72,
            status: "In use",
        },
        { id: 2, name: "Research Storage", type: "Object storage", capacity: "2 TB", usage: 46, status: "Available" },
        { id: 3, name: "CPU Compute Pool", type: "CPU instance", capacity: "16 vCPU", usage: 28, status: "Available" },
    ],
};
const BACKEND_KINDS = ["projects", "datasets", "training"];
let db = { projects: [], datasets: [], training: [] };
const backend = {
    checked: false,
    online: false,
    database: null,
    ai: null,
    aiProvider: "",
    model: "",
    accessKeyRequired: false,
    error: "",
};

function storageGet(key) {
    try {
        return localStorage.getItem(key);
    } catch {
        return null;
    }
}
function storageSet(key, value) {
    try {
        localStorage.setItem(key, value);
        return true;
    } catch {
        return false;
    }
}
function storageRemove(key) {
    try {
        localStorage.removeItem(key);
    } catch {
        /* ignore */
    }
}

async function load() {
    Object.keys(demoDefaults).forEach((k) => {
        try {
            db[k] = JSON.parse(storageGet("researchai_" + k)) || structuredClone(demoDefaults[k]);
        } catch {
            db[k] = structuredClone(demoDefaults[k]);
        }
    });
    // Older versions cached backend records in localStorage, which caused stale/duplicate rows.
    BACKEND_KINDS.forEach((k) => storageRemove("researchai_" + k));
    loadChats();
    render();
    await refreshBackend();
}

function save() {
    Object.keys(demoDefaults).forEach((k) => storageSet("researchai_" + k, JSON.stringify(db[k])));
}

// Online deployments can require a shared access key for changes (APP_ACCESS_KEY on the server).
// It is kept only for this browser tab (sessionStorage) and sent in the X-Access-Key header.
const ACCESS_KEY_STORE = "researchai_access_key";
function getAccessKey() {
    try {
        return sessionStorage.getItem(ACCESS_KEY_STORE) || "";
    } catch {
        return "";
    }
}
function setAccessKey(key) {
    try {
        key ? sessionStorage.setItem(ACCESS_KEY_STORE, key) : sessionStorage.removeItem(ACCESS_KEY_STORE);
    } catch {
        /* ignore */
    }
}
function askAccessKey(reason) {
    const key = prompt(
        `${reason}\n\nThis online demo is protected. Enter the access key to make changes or use the AI:`,
    );
    setAccessKey(key ? key.trim() : "");
    return Boolean(key && key.trim());
}

/** Calls the Spring Boot API and returns parsed JSON, or throws an Error with a readable message. */
async function api(path, options = {}, timeoutMs = 30000, retried = false) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    const headers = {};
    if (options.body && !(options.body instanceof FormData)) headers["Content-Type"] = "application/json";
    if (getAccessKey()) headers["X-Access-Key"] = getAccessKey();
    let response;
    try {
        response = await fetch(API_BASE + path, { ...options, headers, signal: controller.signal });
    } catch (error) {
        throw new Error(
            error.name === "AbortError"
                ? "The request timed out. Please try again."
                : "Cannot reach the backend at " +
                  API_BASE +
                  ". If it is hosted for free it may be waking up — wait a minute and retry.",
        );
    } finally {
        clearTimeout(timer);
    }
    if (response.status === 204) return null;
    let data = null;
    try {
        data = await response.json();
    } catch {
        /* empty or non-JSON body */
    }
    if (response.status === 401 && data && data.code === "ACCESS_KEY_REQUIRED") {
        if (!retried && askAccessKey(data.error)) return api(path, options, timeoutMs, true);
        setAccessKey("");
        throw new Error("Access key required. " + (data.error || ""));
    }
    if (!response.ok) throw new Error((data && data.error) || `Request failed (HTTP ${response.status}).`);
    return data;
}

async function refreshBackend() {
    // Free cloud hosts stop idle servers; the first request can take up to a minute while it starts.
    const slow = setTimeout(() => {
        if (!backend.checked) renderBackendStatus("Waking up server… (can take ~1 min)");
    }, 4000);
    try {
        const health = await api("/health", {}, 100000);
        const [projects, datasets, training] = await Promise.all([
            api("/projects"),
            api("/datasets"),
            api("/training"),
        ]);
        Object.assign(backend, {
            online: true,
            database: health.database,
            ai: health.ai,
            aiProvider: health.aiProvider,
            model: health.model,
            accessKeyRequired: health.accessKeyRequired,
            error: "",
        });
        db.projects = projects;
        db.datasets = datasets;
        db.training = training;
    } catch (error) {
        Object.assign(backend, { online: false, error: error.message });
        db.projects = [];
        db.datasets = [];
        db.training = [];
    }
    clearTimeout(slow);
    backend.checked = true;
    renderBackendStatus();
    render();
    db.training.filter(isActiveJob).forEach((j) => pollJob(j.id));
}

function aiLabel() {
    return backend.aiProvider === "ollama" || !backend.aiProvider
        ? `your local Ollama ${backend.model || "llama3.2:3b"} model`
        : `the ${backend.model} model (${backend.aiProvider === "groq" ? "Groq" : "hosted"} API)`;
}

function renderBackendStatus(waitingText) {
    const pill = $("#backend-status");
    if (!pill) return;
    let text, cls;
    if (!backend.checked) {
        text = waitingText || "Checking backend…";
        cls = "";
    } else if (!backend.online) {
        text = "Backend offline";
        cls = "offline";
    } else if (backend.database !== "UP") {
        text = "Database offline";
        cls = "offline";
    } else if (backend.ai !== "UP") {
        text = "Online · AI offline";
        cls = "warn";
    } else {
        text = "Backend · MySQL · AI online" + (backend.accessKeyRequired ? " · 🔒" : "");
        cls = "";
    }
    pill.title = backend.accessKeyRequired ? "Changes require the demo access key" : "Backend connection status";
    pill.className = "status-pill " + cls;
    pill.innerHTML = `<i></i> <span>${esc(text)}</span>`;
}

/* ===================== Page sections (HTML helpers and render functions) ===================== */

let role = "researcher",
    view = "overview";

const names = {
    overview: "Overview",
    projects: "Projects",
    datasets: "Datasets",
    training: "Model training",
    experiments: "Experiments",
    collaboration: "Collaboration",
    "ai-assistant": "AI Assistant",
    users: "User management",
    resources: "Resources",
    usage: "Usage monitoring",
    profile: "My profile",
};

function esc(s = "") {
    return String(s ?? "").replace(
        /[&<>"']/g,
        (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c],
    );
}
function tag(s) {
    let cls = /running|active|completed|ready|available|in use/i.test(s)
        ? ""
        : /pending|planning|invited|queued|metadata/i.test(s)
          ? "pending"
          : /failed/i.test(s)
            ? "failed"
            : "neutral";
    if (/running/i.test(s)) cls = "running";
    return `<span class="tag ${cls}">${esc(s)}</span>`;
}
function stat(label, num, foot, ico) {
    return `<div class="stat"><div class="stat-top">${label}<span class="stat-icon">${ico}</span></div><div class="stat-num">${num}</div><div class="stat-foot">${foot}</div></div>`;
}
function table(headers, rows, empty = "Nothing here yet. Add your first item to get started.") {
    if (!rows.length) return `<div class="empty">${empty}</div>`;
    return `<div class="table-wrap"><table><thead><tr>${headers.map((h) => `<th>${h}</th>`).join("")}</tr></thead><tbody>${rows.join("")}</tbody></table></div>`;
}
function rowTitle(title, sub = "") {
    return `<strong>${esc(title)}</strong>${sub ? `<div class="panel-sub">${esc(sub)}</div>` : ""}`;
}
function panel(title, sub, body, action = "") {
    return `<section class="panel"><div class="panel-head"><div><h3>${title}</h3>${sub ? `<div class="panel-sub">${sub}</div>` : ""}</div>${action}</div>${body}</section>`;
}
function activity(ico, title, sub) {
    return `<div class="activity"><div class="activity-icon">${ico}</div><div><strong>${title}</strong><small>${sub}</small></div></div>`;
}
function fmt(n, d = 2) {
    return n === null || n === undefined || Number.isNaN(Number(n)) ? "—" : Number(n).toFixed(d);
}
function rowActions(kind, id, editable) {
    return `<td class="actions-cell">${editable ? `<button class="table-actions" data-edit="${kind}" data-id="${id}">Edit</button>` : ""}<button class="table-actions" data-delete="${kind}" data-id="${id}">Delete</button></td>`;
}
function isMlJob(j) {
    return j.jobType === "ML_TRAINING";
}
function isActiveJob(j) {
    return isMlJob(j) && (j.status === "Queued" || j.status === "Running");
}
function demoNote() {
    return `<div class="demo-note">Demo data — stored only in this browser, not in the database.</div>`;
}

/** Shown on database-backed pages when the API cannot be reached. */
function backendNotice() {
    if (!backend.checked || backend.online) return "";
    return `<div class="notice error"><div><strong>Cannot load data from the backend.</strong><div>${esc(backend.error)} Start the backend (see README), then retry.</div></div><button class="secondary" data-action="retry">Retry</button></div>`;
}

function renderOverview() {
    const active = db.projects.filter((p) => p.status === "Active").length,
        running = db.training.filter(isActiveJob).length;
    const runs = db.training
        .filter((j) => isMlJob(j) && j.status === "Completed")
        .slice(0, 2)
        .map(
            (j) =>
                `<tr><td>${rowTitle("Run #" + j.id + " · " + j.model, j.dataset)}</td><td>R² ${fmt(j.r2, 3)} · MAE ${fmt(j.mae)}</td><td>${tag(j.status)}</td></tr>`,
        );
    let recent = runs.concat(
        db.experiments
            .slice(0, 4 - runs.length)
            .map(
                (e) =>
                    `<tr><td>${rowTitle(e.name, e.model)}</td><td>${esc(e.metric)}</td><td>${tag(e.status)}</td></tr>`,
            ),
    );
    let projectRows = db.projects
        .slice(0, 3)
        .map(
            (p) =>
                `<tr><td>${rowTitle(p.title, p.team)}</td><td>${tag(p.status)}</td><td><button class="table-actions" data-delete="projects" data-id="${p.id}">Delete</button></td></tr>`,
        );
    let content = `${backendNotice()}<div class="grid">${stat("Research projects", db.projects.length, `${active} active projects`, "◈")}${stat("Datasets", db.datasets.length, `${db.datasets.filter((d) => d.hasData).length} with CSV data`, "▤")}${stat("Training jobs", db.training.length, `${running} currently running`, "▷")}${stat("Experiments", db.experiments.length + db.training.filter((j) => isMlJob(j) && j.status === "Completed").length, "Training runs + notes", "⌁")}</div>
 <div class="two-col">${panel("Recent projects", "Your latest research work", table(["PROJECT / TEAM", "STATUS", ""], projectRows), `<button class="text-button" data-go="projects">View all →</button>`)}${panel("Workspace activity", "A quick look at recent activity", activity("＋", "Workspace is ready", "Manage research projects and datasets") + activity("▷", "Model training", `${running} job(s) currently running · ${db.training.filter((j) => isMlJob(j) && j.status === "Completed").length} completed run(s)`) + activity("⌁", "Experiment tracking", `${db.experiments.length} experiment note(s) recorded`) + activity("✦", "AI Assistant", backend.ai === "UP" ? `AI model ${esc(backend.model)} is available` : "AI model is offline"))}</div>
 <div class="two-col">${panel("Recent experiments", "Performance and run status", table(["EXPERIMENT", "RESULT", "STATUS"], recent), `<button class="text-button" data-go="experiments">View experiments →</button>`)}${panel("Resource utilization", "Workspace capacity (demo data)", `<div class="progress-row"><div class="progress-label"><span>GPU compute</span><span>72%</span></div><div class="bar"><span style="width:72%"></span></div></div><div class="progress-row"><div class="progress-label"><span>Storage</span><span>46%</span></div><div class="bar teal"><span style="width:46%"></span></div></div><div class="progress-row"><div class="progress-label"><span>CPU pool</span><span>28%</span></div><div class="bar orange"><span style="width:28%"></span></div></div>`)}</div>`;
    return content;
}
function renderProjects() {
    return (
        backendNotice() +
        panel(
            "Research projects",
            "Create and manage AI research projects (saved in MySQL)",
            table(
                ["PROJECT", "TEAM", "STATUS", "ACTIONS"],
                db.projects.map(
                    (p) =>
                        `<tr><td>${rowTitle(p.title, p.description)}</td><td>${esc(p.team || "—")}</td><td>${tag(p.status)}</td>${rowActions("projects", p.id, true)}</tr>`,
                ),
            ),
            `<button class="primary" data-add="projects">＋ New project</button>`,
        )
    );
}
function renderDatasets() {
    return (
        backendNotice() +
        panel(
            "Dataset library",
            "Upload CSV files for model training, or record metadata for other datasets (saved in MySQL)",
            table(
                ["DATASET", "DESCRIPTION", "OWNER", "FILE", "DATA", "STATUS", "ACTIONS"],
                db.datasets.map(
                    (d) =>
                        `<tr><td>${rowTitle(d.name)}</td><td>${esc(d.description || "—")}</td><td>${esc(d.owner || "—")}</td><td>${esc(d.fileName || "—")}</td><td>${d.hasData ? `${d.rowCount} rows · ${d.columns.length} cols` : "—"}</td><td>${tag(d.status)}</td>${rowActions("datasets", d.id, true)}</tr>`,
                ),
            ),
            `<div class="button-row"><button class="secondary" data-action="load-sample">Load sample CSV</button><button class="primary" data-add="datasets">＋ Add dataset</button></div>`,
        )
    );
}
function renderExperiments() {
    const runs = db.training
        .filter(isMlJob)
        .map(
            (j) =>
                `<tr><td>${rowTitle("Run #" + j.id, j.dataset)}</td><td>${esc(j.model)}</td><td>${j.status === "Completed" ? `R² ${fmt(j.r2, 3)} · MAE ${fmt(j.mae)} · RMSE ${fmt(j.rmse)}` : esc(j.errorMessage || "—")}</td><td>${tag(j.status)}</td><td><button class="table-actions" data-action="view-job" data-id="${j.id}">View</button></td></tr>`,
        );
    return (
        backendNotice() +
        panel(
            "Model training runs",
            "Real runs from the Model training page, with test-set metrics (saved in MySQL)",
            table(
                ["RUN", "MODEL", "METRICS", "STATUS", ""],
                runs,
                "No training runs yet. Open Model training to run one.",
            ),
        ) +
        panel(
            "Experiment notes",
            "Manually logged notes about other experiments",
            demoNote() +
                table(
                    ["EXPERIMENT", "MODEL", "METRIC / RESULT", "STATUS", ""],
                    db.experiments.map(
                        (e) =>
                            `<tr><td>${rowTitle(e.name)}</td><td>${esc(e.model)}</td><td>${esc(e.metric)}</td><td>${tag(e.status)}</td><td><button class="table-actions" data-delete="experiments" data-id="${e.id}">Delete</button></td></tr>`,
                    ),
                ),
            `<button class="primary" data-add="experiments">＋ Log experiment</button>`,
        )
    );
}
function renderCollaboration() {
    return panel(
        "Collaborative projects",
        "Coordinate team members and shared research",
        demoNote() +
            table(
                ["PROJECT", "TEAM MEMBERS", "STATUS", ""],
                db.collaboration.map(
                    (c) =>
                        `<tr><td>${rowTitle(c.project)}</td><td>${esc(c.members)}</td><td>${tag(c.status)}</td><td><button class="table-actions" data-delete="collaboration" data-id="${c.id}">Remove</button></td></tr>`,
                ),
            ),
        `<button class="primary" data-add="collaboration">＋ Start collaboration</button>`,
    );
}
function renderUsers() {
    return panel(
        "Platform users",
        "Admin-only account overview",
        `<div class="demo-note">Demo data stored in this browser. The Researcher/Admin switch at the top only changes the view — it is not a login and provides no security.</div>` +
            table(
                ["NAME", "EMAIL", "ROLE", "STATUS", ""],
                db.users.map(
                    (u) =>
                        `<tr><td>${rowTitle(u.name)}</td><td>${esc(u.email)}</td><td>${tag(u.role)}</td><td>${tag(u.status)}</td><td><button class="table-actions" data-delete="users" data-id="${u.id}">Delete</button></td></tr>`,
                ),
            ),
        `<button class="primary" data-add="users">＋ Add user</button>`,
    );
}
function renderResources() {
    return panel(
        "Compute & storage resources",
        "Monitor resources available to research teams",
        demoNote() +
            table(
                ["RESOURCE", "TYPE", "CAPACITY", "UTILIZATION", "STATUS", ""],
                db.resources.map(
                    (r) =>
                        `<tr><td>${rowTitle(r.name)}</td><td>${esc(r.type)}</td><td>${esc(r.capacity)}</td><td><div style="min-width:100px"><div class="progress-label"><span>${Number(r.usage)}%</span></div><div class="bar"><span style="width:${Number(r.usage)}%"></span></div></div></td><td>${tag(r.status)}</td><td><button class="table-actions" data-delete="resources" data-id="${r.id}">Delete</button></td></tr>`,
                ),
            ),
        `<button class="primary" data-add="resources">＋ Add resource</button>`,
    );
}
function renderUsage() {
    let total = db.resources.length;
    return `<div class="grid">${stat("Compute resources", db.resources.filter((r) => /compute|gpu|cpu/i.test(r.type + " " + r.name)).length, "Registered resources", "▣")}${stat("Storage resources", db.resources.filter((r) => /storage/i.test(r.type + " " + r.name)).length, "Storage pools", "▤")}${stat("Average utilization", Math.round(db.resources.reduce((a, r) => a + Number(r.usage), 0) / (total || 1)) + "%", "Across listed resources", "▥")}${stat("Active projects", db.projects.filter((p) => p.status === "Active").length, "Current research projects", "◈")}</div>${panel("Usage breakdown", "Illustrative usage data from resources (demo)", db.resources.map((r) => `<div class="progress-row"><div class="progress-label"><span>${esc(r.name)}</span><span>${Number(r.usage)}%</span></div><div class="bar ${r.usage > 65 ? "" : "teal"}"><span style="width:${Number(r.usage)}%"></span></div></div>`).join(""))}`;
}
function renderProfile() {
    return panel(
        "Profile information",
        "Update your workspace profile",
        `<div class="demo-note">Demo only — profile changes are not saved.</div><form id="profile-form"><div class="form-grid"><div class="field"><label>Full name</label><input name="name" value="Keshav Tomar" required></div><div class="field"><label>Email address</label><input name="email" type="email" value="keshav@example.com" required></div><div class="field full"><label>Role</label><input value="${role === "admin" ? "Admin" : "Researcher"} (demo)" disabled></div></div><button class="primary" type="submit">Save profile</button></form>`,
    );
}

/* ===================== AI Assistant (chat history in localStorage) ===================== */

const CHAT_KEY = "researchai_chats_v2";
const OLD_CHAT_KEY = "researchAIChatHistory";
const CHAT_CONTEXT = 20;
let chatState = { activeId: null, conversations: [] };
const chatPending = new Set();
let chatDraft = "";

function newId() {
    return Date.now().toString(36) + Math.random().toString(36).slice(2, 7);
}

function loadChats() {
    try {
        const parsed = JSON.parse(storageGet(CHAT_KEY));
        if (parsed && Array.isArray(parsed.conversations)) {
            chatState.conversations = parsed.conversations
                .filter((c) => c && typeof c.id === "string" && Array.isArray(c.messages))
                .map((c) => ({
                    ...c,
                    messages: c.messages.filter(
                        (m) => m && ["user", "assistant", "error"].includes(m.role) && typeof m.content === "string",
                    ),
                }));
            chatState.activeId = parsed.activeId;
        }
    } catch {
        /* corrupted storage: start fresh */
    }
    migrateOldChat();
    if (!activeChat()) {
        if (chatState.conversations.length) chatState.activeId = chatState.conversations[0].id;
        else createChat();
    }
}

/** Converts the old HTML-based history into plain-text messages (the HTML is parsed inertly, never executed). */
function migrateOldChat() {
    const old = storageGet(OLD_CHAT_KEY);
    if (!old) return;
    try {
        const doc = new DOMParser().parseFromString(old, "text/html");
        const messages = [];
        doc.querySelectorAll(".activity").forEach((el) => {
            const who = el.querySelector("strong")?.textContent.trim();
            const text = el.querySelector("small")?.innerText ?? el.querySelector("small")?.textContent ?? "";
            if (!text.trim() || /^Hello! Ask me a question/.test(text.trim())) return;
            messages.push({
                role: who === "You" ? "user" : who === "AI Assistant" ? "assistant" : "error",
                content: text.trim(),
                at: Date.now(),
            });
        });
        if (messages.length) {
            const conv = {
                id: newId(),
                title: chatTitle(messages),
                messages,
                createdAt: Date.now(),
                updatedAt: Date.now(),
            };
            chatState.conversations.unshift(conv);
            chatState.activeId = conv.id;
        }
    } catch {
        /* ignore unreadable old history */
    }
    storageRemove(OLD_CHAT_KEY);
    saveChats();
}

function saveChats() {
    let data = JSON.stringify(chatState);
    // If storage is full, drop the oldest conversations until it fits.
    while (!storageSet(CHAT_KEY, data) && chatState.conversations.length > 1) {
        chatState.conversations.pop();
        data = JSON.stringify(chatState);
    }
}

function activeChat() {
    return chatState.conversations.find((c) => c.id === chatState.activeId);
}
function chatTitle(messages) {
    const first = messages.find((m) => m.role === "user");
    return first ? first.content.replace(/\s+/g, " ").slice(0, 40) : "New chat";
}

function createChat() {
    const conv = { id: newId(), title: "New chat", messages: [], createdAt: Date.now(), updatedAt: Date.now() };
    chatState.conversations.unshift(conv);
    chatState.activeId = conv.id;
    saveChats();
    return conv;
}

function newChat() {
    const current = activeChat();
    if (!current || current.messages.length) createChat();
    chatDraft = "";
    renderChat();
    $("#ai-prompt")?.focus();
}

function deleteChat(id) {
    chatState.conversations = chatState.conversations.filter((c) => c.id !== id);
    if (chatState.activeId === id) chatState.activeId = chatState.conversations[0]?.id ?? null;
    if (!activeChat()) createChat();
    saveChats();
    renderChat();
}

/** Very small Markdown renderer. Every piece of text is HTML-escaped first, so model output cannot inject HTML. */
function renderMarkdown(src) {
    const lines = String(src).replace(/\r\n?/g, "\n").split("\n");
    let html = "",
        list = null,
        inCode = false,
        code = [];
    const closeList = () => {
        if (list) {
            html += `</${list}>`;
            list = null;
        }
    };
    for (const line of lines) {
        if (/^\s*```/.test(line)) {
            if (inCode) {
                html += `<pre><code>${esc(code.join("\n"))}</code></pre>`;
                code = [];
                inCode = false;
            } else {
                closeList();
                inCode = true;
            }
            continue;
        }
        if (inCode) {
            code.push(line);
            continue;
        }
        let m;
        if ((m = line.match(/^\s*#{1,6}\s+(.*)$/))) {
            closeList();
            html += `<h4>${inlineMarkdown(m[1])}</h4>`;
            continue;
        }
        if ((m = line.match(/^\s*[-*•]\s+(.*)$/))) {
            if (list !== "ul") {
                closeList();
                html += "<ul>";
                list = "ul";
            }
            html += `<li>${inlineMarkdown(m[1])}</li>`;
            continue;
        }
        if ((m = line.match(/^\s*\d+[.)]\s+(.*)$/))) {
            if (list !== "ol") {
                closeList();
                html += "<ol>";
                list = "ol";
            }
            html += `<li>${inlineMarkdown(m[1])}</li>`;
            continue;
        }
        closeList();
        if (line.trim()) html += `<p>${inlineMarkdown(line)}</p>`;
    }
    if (inCode) html += `<pre><code>${esc(code.join("\n"))}</code></pre>`;
    closeList();
    return html;
}

function inlineMarkdown(text) {
    return text
        .split(/(`[^`]+`)/)
        .map((part) =>
            /^`[^`]+`$/.test(part)
                ? `<code>${esc(part.slice(1, -1))}</code>`
                : esc(part)
                      .replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>")
                      .replace(/(^|[^*])\*([^*\s][^*]*?)\*(?!\*)/g, "$1<em>$2</em>"),
        )
        .join("");
}

function messageHtml(m) {
    if (m.role === "user")
        return `<div class="activity chat-user"><div class="activity-icon">👤</div><div class="msg"><strong>You</strong><div class="msg-body">${esc(m.content).replace(/\n/g, "<br>")}</div></div></div>`;
    if (m.role === "assistant")
        return `<div class="activity ai-message"><div class="activity-icon">✦</div><div class="msg"><strong>AI Assistant</strong><div class="msg-body ai-response">${renderMarkdown(m.content)}</div></div></div>`;
    return `<div class="activity chat-error"><div class="activity-icon">!</div><div class="msg"><strong>Could not get an answer</strong><div class="msg-body">${esc(m.content)}</div></div></div>`;
}

function chatMessagesHtml() {
    const conv = activeChat();
    const welcome = `<div class="activity"><div class="activity-icon">✦</div><div class="msg"><strong>AI Assistant</strong><div class="msg-body">Hello! Ask me a question or get help with your studies.</div></div></div>`;
    const typing =
        conv && chatPending.has(conv.id)
            ? `<div class="activity ai-message"><div class="activity-icon">✦</div><div class="msg"><strong>AI Assistant</strong><div class="msg-body typing">Thinking<span>.</span><span>.</span><span>.</span></div></div></div>`
            : "";
    return welcome + (conv ? conv.messages.map(messageHtml).join("") : "") + typing;
}

function chatListHtml() {
    return chatState.conversations
        .map(
            (c) =>
                `<div class="chat-item ${c.id === chatState.activeId ? "active" : ""}"><button type="button" class="chat-open" data-chat-open="${esc(c.id)}" title="${esc(c.title)}">${esc(c.title)}${chatPending.has(c.id) ? " …" : ""}</button><button type="button" class="chat-delete" data-chat-delete="${esc(c.id)}" aria-label="Delete conversation">×</button></div>`,
        )
        .join("");
}

function renderAiAssistant() {
    const offline = backend.checked && (!backend.online || backend.ai !== "UP");
    return `
 <section class="panel">
   <div class="panel-head">
     <div class="ai-heading-row">
       <div>
         <h3>AI Assistant</h3>
         <div class="panel-sub">Powered by ${esc(aiLabel())} · history is saved in this browser</div>
       </div>
       <button type="button" class="secondary" id="ai-new-chat">+ New Chat</button>
     </div>
   </div>
   ${offline ? `<div class="notice warn">${!backend.online ? "The backend is offline, so the assistant cannot answer." : backend.aiProvider === "ollama" ? "Ollama is not reachable. Start the Ollama app (or run <code>ollama serve</code>)." : "The AI service is not reachable right now. Check the AI_API_KEY setting on the server."}</div>` : ""}
   <div class="chat-layout">
     <aside class="chat-list" id="chat-list" aria-label="Conversations">${chatListHtml()}</aside>
     <div class="chat-main">
       <div id="ai-chat" aria-live="polite">${chatMessagesHtml()}</div>
       <form id="ai-chat-form">
         <div class="field">
           <label for="ai-prompt">Your message <span class="hint">(Enter to send · Shift+Enter for a new line)</span></label>
           <textarea id="ai-prompt" name="prompt" rows="3" maxlength="4000" placeholder="Ask anything...">${esc(chatDraft)}</textarea>
         </div>
         <button class="primary" id="ai-send" type="submit">Send message</button>
         <p class="panel-sub">${backend.aiProvider === "ollama" || !backend.aiProvider ? "Make sure Ollama and your Spring Boot backend are running." : "Free AI plans have limits: if the AI is busy, wait a minute."}</p>
       </form>
     </div>
   </div>
 </section>`;
}

/** Re-renders only the chat parts, so the text box keeps its content and focus. */
function renderChat() {
    if (view !== "ai-assistant") return;
    const chat = $("#ai-chat"),
        list = $("#chat-list"),
        send = $("#ai-send"),
        prompt = $("#ai-prompt");
    if (!chat) return;
    chat.innerHTML = chatMessagesHtml();
    list.innerHTML = chatListHtml();
    const pending = chatPending.has(chatState.activeId);
    send.disabled = pending;
    send.textContent = pending ? "Thinking..." : "Send message";
    if (prompt.value !== chatDraft) prompt.value = chatDraft;
    chat.scrollTop = chat.scrollHeight;
}

async function sendChat() {
    const conv = activeChat();
    const text = chatDraft.trim();
    if (!conv || !text || chatPending.has(conv.id)) return;
    if (text.length > 4000) {
        toast("Message is too long (max 4000 characters).", "error");
        return;
    }

    conv.messages.push({ role: "user", content: text, at: Date.now() });
    if (conv.title === "New chat") conv.title = chatTitle(conv.messages);
    conv.updatedAt = Date.now();
    chatDraft = "";
    chatPending.add(conv.id);
    saveChats();
    renderChat();

    const context = conv.messages
        .filter((m) => m.role !== "error")
        .slice(-CHAT_CONTEXT)
        .map(({ role, content }) => ({ role, content }));
    try {
        const data = await api("/ai/chat", { method: "POST", body: JSON.stringify({ messages: context }) }, 190000);
        const answer = data && typeof data.response === "string" ? data.response.trim() : "";
        conv.messages.push(
            answer
                ? { role: "assistant", content: answer, at: Date.now() }
                : { role: "error", content: "The AI returned an empty response. Please try again.", at: Date.now() },
        );
    } catch (error) {
        conv.messages.push({ role: "error", content: error.message, at: Date.now() });
    } finally {
        chatPending.delete(conv.id);
        conv.updatedAt = Date.now();
        saveChats();
        renderChat();
        if (view === "ai-assistant" && chatState.activeId === conv.id) $("#ai-prompt")?.focus();
    }
}

/* ===================== Model training (real linear regression on the backend) ===================== */

const ml = {
    datasetId: null,
    target: null,
    features: new Set(),
    testRatio: 0.2,
    seed: 42,
    previews: {},
    previewError: "",
    selectedJobId: null,
    prediction: null,
    starting: false,
};
const polling = new Set();

function trainableDatasets() {
    return db.datasets.filter((d) => d.hasData);
}

async function ensureMlSelection() {
    const sets = trainableDatasets();
    if (!sets.length) {
        ml.datasetId = null;
        return;
    }
    if (!sets.some((d) => d.id === ml.datasetId)) ml.datasetId = sets[0].id;
    await loadPreview(ml.datasetId);
}

async function loadPreview(id) {
    if (!id || ml.previews[id]) return;
    try {
        ml.previewError = "";
        ml.previews[id] = await api(`/datasets/${id}/preview`);
        const p = ml.previews[id];
        if (ml.datasetId === id) {
            ml.target = p.numericColumns.includes("final_score")
                ? "final_score"
                : p.numericColumns[p.numericColumns.length - 1] || null;
            ml.features = new Set(p.numericColumns.filter((c) => c !== ml.target));
        }
    } catch (error) {
        ml.previewError = error.message;
    }
    refreshTrainingViews(true);
}

function mlFormHtml() {
    const sets = trainableDatasets();
    if (!backend.online) return `<div class="empty">Start the backend to train models.</div>`;
    if (!sets.length)
        return `<div class="ml-empty"><p>No dataset with CSV data yet. Load the bundled sample (200 synthetic student records) or upload your own CSV on the Datasets page.</p><div class="button-row"><button class="primary" data-action="load-sample">Load sample dataset</button><button class="secondary" data-go="datasets">Upload a CSV</button></div></div>`;
    const p = ml.previews[ml.datasetId];
    const options = sets
        .map(
            (d) =>
                `<option value="${d.id}" ${d.id === ml.datasetId ? "selected" : ""}>${esc(d.name)} (${d.rowCount} rows)</option>`,
        )
        .join("");
    let details = `<div class="empty">${ml.previewError ? esc(ml.previewError) : "Loading dataset preview…"}</div>`;
    if (p) {
        const nonNumeric = p.columns.filter((c) => !p.numericColumns.includes(c));
        const missingTotal = Object.values(p.missingValues).reduce((a, b) => a + b, 0);
        details = `
        <div class="ml-checks">
          <span>✔ ${p.totalRows} rows · ${p.columns.length} columns</span>
          <span>${missingTotal ? `⚠ ${missingTotal} missing value(s) — rows with a missing value in a selected column are dropped` : "✔ No missing values"}</span>
          ${nonNumeric.length ? `<span>ⓘ Not numeric (cannot be used): ${nonNumeric.map(esc).join(", ")}</span>` : ""}
        </div>
        <div class="table-wrap preview-table"><table><thead><tr>${p.columns.map((c) => `<th>${esc(c)}${p.missingValues[c] ? ` <span class="hint">(${p.missingValues[c]} missing)</span>` : ""}</th>`).join("")}</tr></thead><tbody>${p.rows
            .slice(0, 5)
            .map((r) => `<tr>${r.map((v) => `<td>${esc(v) || '<span class="hint">empty</span>'}</td>`).join("")}</tr>`)
            .join("")}</tbody></table></div>
        <div class="form-grid">
          <div class="field"><label for="ml-target">Target column (what to predict) *</label><select id="ml-target">${p.numericColumns.map((c) => `<option ${c === ml.target ? "selected" : ""}>${esc(c)}</option>`).join("")}</select></div>
          <div class="field"><label>Test split *</label><select id="ml-test">${[0.1, 0.2, 0.3, 0.4, 0.5].map((r) => `<option value="${r}" ${r === ml.testRatio ? "selected" : ""}>${r * 100}% test / ${100 - r * 100}% train</option>`).join("")}</select></div>
          <div class="field full"><label>Feature columns (inputs) *</label><div class="checkbox-grid">${p.numericColumns
              .filter((c) => c !== ml.target)
              .map(
                  (c) =>
                      `<label class="check"><input type="checkbox" data-feature="${esc(c)}" ${ml.features.has(c) ? "checked" : ""}> ${esc(c)}</label>`,
              )
              .join("")}</div></div>
          <div class="field"><label for="ml-seed">Random seed (same seed = same split)</label><input id="ml-seed" type="number" step="1" value="${ml.seed}"></div>
        </div>
        <button type="submit" class="primary" id="ml-train" ${ml.starting ? "disabled" : ""}>${ml.starting ? "Starting…" : "▷ Train model"}</button>`;
    }
    return `<form id="ml-form" novalidate>
      <div class="field"><label for="ml-dataset">Dataset (CSV)</label><select id="ml-dataset">${options}</select></div>
      ${details}
    </form>`;
}

function selectedJob() {
    const runs = db.training.filter(isMlJob);
    return runs.find((j) => j.id === ml.selectedJobId) || runs[0] || null;
}

function mlResultHtml() {
    const j = selectedJob();
    if (!j) return `<div class="empty">No training runs yet. Choose a dataset and click “Train model”.</div>`;
    const head = `<div class="result-head"><strong>Run #${j.id} · ${esc(j.model)} on ${esc(j.dataset)}</strong>${tag(j.status)}</div><div class="panel-sub">${esc(j.parameters || "")}</div>`;
    if (isActiveJob(j))
        return (
            head +
            `<div class="progress-row"><div class="progress-label"><span>${esc(j.status)}…</span><span>${j.progress || 0}%</span></div><div class="bar"><span style="width:${j.progress || 0}%"></span></div></div>`
        );
    if (j.status === "Failed")
        return (
            head +
            `<div class="notice error"><div><strong>Training failed.</strong><div>${esc(j.errorMessage || "Unknown error")}</div></div></div>`
        );
    const coefs = j.coefficients || {};
    const target = esc(j.targetColumn);
    const r2pct = j.r2 === null ? "—" : Math.round(j.r2 * 1000) / 10 + "%";
    const overfit = j.trainR2 !== null && j.r2 !== null && j.trainR2 - j.r2 > 0.1;
    const equation =
        `${target} = ${fmt(j.intercept, 3)} ` +
        Object.entries(coefs)
            .map(([f, c]) => `${c < 0 ? "−" : "+"} ${fmt(Math.abs(c), 3)} × ${esc(f)}`)
            .join(" ");
    const pred = ml.prediction && ml.prediction.jobId === j.id ? ml.prediction : null;
    return (
        head +
        `
      <div class="metric-grid">
        <div class="metric"><span>R² (test)</span><b>${fmt(j.r2, 3)}</b><small>Explains ${r2pct} of the variation in ${target}</small></div>
        <div class="metric"><span>MAE (test)</span><b>${fmt(j.mae)}</b><small>Average error, in ${target} units</small></div>
        <div class="metric"><span>RMSE (test)</span><b>${fmt(j.rmse)}</b><small>Penalises big errors more</small></div>
        <div class="metric"><span>Baseline MAE</span><b>${fmt(j.baselineMae)}</b><small>Always guessing the training average</small></div>
      </div>
      <p class="ml-explain">The model was trained on <b>${j.trainRows}</b> rows and evaluated on <b>${j.testRows}</b> unseen test rows${j.droppedRows ? ` (${j.droppedRows} incomplete row(s) dropped)` : ""}. Its predictions were off by <b>${fmt(j.mae)}</b> on average, compared with <b>${fmt(j.baselineMae)}</b> for the naive baseline. Training R² was ${fmt(j.trainR2, 3)}${overfit ? " — noticeably higher than test R², which suggests overfitting" : ", close to the test R², so there is no sign of overfitting"}. Duration: ${j.durationMs ?? "—"} ms.</p>
      <div class="equation"><span class="hint">Learned equation</span><code>${equation}</code></div>
      ${table(
          ["FEATURE", "COEFFICIENT", "MEANING"],
          Object.entries(coefs).map(
              ([f, c]) =>
                  `<tr><td>${esc(f)}</td><td>${fmt(c, 4)}</td><td>+1 ${esc(f)} → ${c < 0 ? "" : "+"}${fmt(c, 3)} ${target}</td></tr>`,
          ),
      )}
      <form id="predict-form" data-job="${j.id}" class="predict-form">
        <h4>Try a prediction</h4>
        <div class="form-grid">${Object.keys(coefs)
            .map(
                (f) =>
                    `<div class="field"><label>${esc(f)}</label><input type="number" step="any" required name="${esc(f)}" value="${esc(pred?.inputs?.[f] ?? "")}"></div>`,
            )
            .join("")}</div>
        <button type="submit" class="secondary">Predict ${target}</button>
        ${pred ? (pred.error ? `<div class="form-error">${esc(pred.error)}</div>` : `<div class="prediction">Predicted ${target}: <b>${fmt(pred.value)}</b></div>`) : ""}
      </form>`
    );
}

function jobsTableHtml() {
    return table(
        ["MODEL", "DATASET", "PARAMETERS", "PROGRESS", "STATUS", "RESULT", "ACTIONS"],
        db.training.map((t) => {
            const real = isMlJob(t);
            const progress = real
                ? `<div style="min-width:100px"><div class="progress-label"><span>${t.progress || 0}%</span></div><div class="bar ${t.status === "Failed" ? "orange" : ""}"><span style="width:${t.progress || 0}%"></span></div></div>`
                : `<span class="hint">Not executed</span>`;
            const result = real
                ? t.status === "Completed"
                    ? `R² ${fmt(t.r2, 3)} · MAE ${fmt(t.mae)}`
                    : t.status === "Failed"
                      ? `<span class="hint">${esc((t.errorMessage || "").slice(0, 60))}</span>`
                      : "—"
                : "—";
            return `<tr><td>${rowTitle(t.model, real ? "Run #" + t.id : "")}</td><td>${esc(t.dataset)}</td><td>${esc(t.parameters || "—")}</td><td>${progress}</td><td>${real ? tag(t.status) : tag("Record only")}</td><td>${result}</td><td class="actions-cell">${real ? `<button class="table-actions" data-action="view-job" data-id="${t.id}">View</button>` : ""}<button class="table-actions" data-delete="training" data-id="${t.id}">Delete</button></td></tr>`;
        }),
        "No training jobs yet.",
    );
}

function renderTraining() {
    if (backend.online) ensureMlSelection();
    return (
        backendNotice() +
        `<section class="panel" id="ml-panel"><div class="panel-head"><div><h3>Train a real model</h3><div class="panel-sub">Linear regression (ordinary least squares) runs in the Java backend: validate data → split train/test → fit → evaluate on unseen rows.</div></div></div><div id="ml-form-wrap">${mlFormHtml()}</div></section>` +
        `<section class="panel"><div class="panel-head"><div><h3>Training result</h3><div class="panel-sub">Metrics are calculated on the test rows the model never saw during training</div></div></div><div id="ml-result">${mlResultHtml()}</div></section>` +
        `<section class="panel"><div class="panel-head"><div><h3>Training jobs</h3><div class="panel-sub">All runs are saved in MySQL. “Record only” rows are notes about jobs run elsewhere — nothing is executed for them.</div></div><button class="secondary" data-add="training">Log external job</button></div><div id="jobs-table">${jobsTableHtml()}</div></section>`
    );
}

/** Updates the training page in place (keeps form selections) or re-renders other pages that show jobs. */
function refreshTrainingViews(includeForm = false) {
    if (view === "training" && $("#ml-result")) {
        if (includeForm) $("#ml-form-wrap").innerHTML = mlFormHtml();
        $("#ml-result").innerHTML = mlResultHtml();
        $("#jobs-table").innerHTML = jobsTableHtml();
    } else if (view === "overview" || view === "experiments") {
        render();
    }
}

function upsertJob(job) {
    const i = db.training.findIndex((j) => j.id === job.id);
    if (i >= 0) db.training[i] = job;
    else db.training.unshift(job);
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function pollJob(id) {
    if (polling.has(id)) return;
    polling.add(id);
    try {
        for (let i = 0; i < 600; i++) {
            await sleep(i < 10 ? 300 : 1000);
            let job;
            try {
                job = await api(`/training/${id}`);
            } catch (error) {
                if (/not found/i.test(error.message)) return;
                continue;
            }
            upsertJob(job);
            refreshTrainingViews();
            if (job.status === "Completed") {
                toast(`Training run #${id} finished: R² ${fmt(job.r2, 3)}, MAE ${fmt(job.mae)}.`);
                return;
            }
            if (job.status === "Failed") {
                toast(`Training run #${id} failed: ${job.errorMessage}`, "error");
                return;
            }
        }
    } finally {
        polling.delete(id);
    }
}

async function startTraining() {
    if (ml.starting) return;
    if (!ml.target) {
        toast("Choose a target column.", "error");
        return;
    }
    if (!ml.features.size) {
        toast("Choose at least one feature column.", "error");
        return;
    }
    const seed = Number($("#ml-seed")?.value);
    if (!Number.isInteger(seed)) {
        toast("Random seed must be a whole number.", "error");
        return;
    }
    ml.seed = seed;
    ml.starting = true;
    refreshTrainingViews(true);
    try {
        const job = await api("/training/run", {
            method: "POST",
            body: JSON.stringify({
                datasetId: ml.datasetId,
                targetColumn: ml.target,
                featureColumns: [...ml.features],
                testRatio: ml.testRatio,
                randomSeed: ml.seed,
            }),
        });
        upsertJob(job);
        ml.selectedJobId = job.id;
        toast(`Training run #${job.id} queued.`);
        pollJob(job.id);
    } catch (error) {
        toast("Could not start training: " + error.message, "error");
    } finally {
        ml.starting = false;
        refreshTrainingViews(true);
    }
}

async function predict(form) {
    const jobId = Number(form.dataset.job);
    const inputs = Object.fromEntries(new FormData(form).entries());
    const features = {};
    for (const [k, v] of Object.entries(inputs)) {
        if (v === "" || !Number.isFinite(Number(v))) {
            ml.prediction = { jobId, inputs, error: `Enter a number for ${k}.` };
            refreshTrainingViews();
            return;
        }
        features[k] = Number(v);
    }
    try {
        const r = await api(`/training/${jobId}/predict`, { method: "POST", body: JSON.stringify({ features }) });
        ml.prediction = { jobId, inputs, value: r.prediction };
    } catch (error) {
        ml.prediction = { jobId, inputs, error: error.message };
    }
    refreshTrainingViews();
}

async function loadSampleDataset() {
    try {
        const d = await api("/datasets/sample", { method: "POST" });
        const i = db.datasets.findIndex((x) => x.id === d.id);
        if (i >= 0) db.datasets[i] = d;
        else db.datasets.unshift(d);
        ml.datasetId = d.id;
        toast(i >= 0 ? "The sample dataset is already loaded." : "Sample dataset saved to the database.");
        render();
    } catch (error) {
        toast("Could not load the sample dataset: " + error.message, "error");
    }
}

/* ===================== Page rendering ===================== */

function render() {
    $$(".nav-item").forEach((b) => b.classList.toggle("active", b.dataset.view === view));
    $$(".admin-only").forEach((b) => (b.style.display = role === "admin" ? "flex" : "none"));
    if (role !== "admin" && ["users", "resources", "usage"].includes(view)) view = "overview";
    $("#crumb-page").textContent = names[view];
    $("#page-title").innerHTML = view === "overview" ? "Good morning, Keshav <span>✦</span>" : names[view];
    $("#page-subtitle").textContent = {
        overview: "Your research workspace, all in one place.",
        projects: "Plan, organize, and manage your research initiatives.",
        datasets: "Keep your training data organized and discoverable.",
        training: "Train a real model on a CSV dataset and evaluate it.",
        experiments: "Record results so your research stays reproducible.",
        collaboration: "Share research with teammates and manage project access.",
        "ai-assistant": "Chat with a local AI model running on your computer.",
        users: "Create accounts and manage platform access.",
        resources: "Manage compute instances and storage allocations.",
        usage: "Review compute and storage consumption.",
        profile: "Manage your personal workspace information.",
    }[view];
    $("#main-action").style.display = [
        "overview",
        "projects",
        "datasets",
        "training",
        "experiments",
        "collaboration",
        "users",
        "resources",
    ].includes(view)
        ? "inline-block"
        : "none";
    $("#main-action").textContent =
        {
            overview: "＋ New project",
            projects: "＋ New project",
            datasets: "＋ Add dataset",
            training: "▷ Train a model",
            experiments: "＋ Log experiment",
            collaboration: "＋ Start collaboration",
            users: "＋ Add user",
            resources: "＋ Add resource",
        }[view] || "";
    $("#content").innerHTML = {
        overview: renderOverview,
        projects: renderProjects,
        datasets: renderDatasets,
        training: renderTraining,
        experiments: renderExperiments,
        collaboration: renderCollaboration,
        "ai-assistant": renderAiAssistant,
        users: renderUsers,
        resources: renderResources,
        usage: renderUsage,
        profile: renderProfile,
    }[view]();
    $("#side-role").textContent = role === "admin" ? "Administrator (demo)" : "Researcher (demo)";
    if (view === "ai-assistant") renderChat();
}

/* ===================== Create / edit modal ===================== */

const configs = {
    projects: {
        title: "Create research project",
        editTitle: "Edit research project",
        fields: [
            ["title", "Project title", "text", true],
            ["description", "Description", "textarea", false],
            ["team", "Team members", "text", false],
            ["status", "Status", "select", true, ["Active", "Planning", "Completed", "On hold"]],
        ],
    },
    datasets: {
        title: "Add dataset",
        editTitle: "Edit dataset",
        fields: [
            ["name", "Dataset name", "text", true],
            ["description", "Description", "textarea", false],
            ["owner", "Owner", "text", true],
            ["file", "CSV file (optional, max 2 MB) — needed for model training", "file", false],
            ["fileName", "File name / reference (if no CSV)", "text", false],
        ],
    },
    training: {
        title: "Log external training job",
        fields: [
            ["model", "Model name", "text", true],
            ["dataset", "Dataset name", "text", true],
            ["parameters", "Training parameters", "text", false],
        ],
        note: "This only records a job that ran somewhere else. To actually train a model, use “Train a real model” on this page.",
    },
    experiments: {
        title: "Log experiment",
        fields: [
            ["name", "Experiment name", "text", true],
            ["model", "Model name", "text", true],
            ["metric", "Result / performance metric", "text", true],
            ["status", "Status", "select", true, ["Completed", "Running", "Failed"]],
        ],
    },
    collaboration: {
        title: "Start collaboration",
        fields: [
            ["project", "Project title", "text", true],
            ["members", "Team members", "text", true],
            ["status", "Status", "select", true, ["Active", "Invited"]],
        ],
    },
    users: {
        title: "Create user",
        fields: [
            ["name", "Full name", "text", true],
            ["email", "Email address", "email", true],
            ["role", "Role", "select", true, ["Researcher", "Admin"]],
            ["status", "Status", "select", true, ["Active", "Inactive"]],
        ],
    },
    resources: {
        title: "Add resource",
        fields: [
            ["name", "Resource name", "text", true],
            ["type", "Resource type", "text", true],
            ["capacity", "Capacity", "text", true],
            ["usage", "Utilization (%)", "number", true],
        ],
    },
};
let modalSaving = false;

function fieldHtml([n, label, type, req, opts], item) {
    const value = item ? esc(item[n] ?? "") : n === "usage" ? "0" : "";
    const attrs = `name="${n}" id="f-${n}" ${req ? "required" : ""}`;
    let control;
    if (type === "textarea") control = `<textarea ${attrs} maxlength="2000">${value}</textarea>`;
    else if (type === "select")
        control = `<select ${attrs}>${opts.map((o) => `<option ${item && item[n] === o ? "selected" : ""}>${o}</option>`).join("")}</select>`;
    else if (type === "file") control = `<input ${attrs} type="file" accept=".csv,text/csv">`;
    else
        control = `<input ${attrs} type="${type}" maxlength="200" value="${value}" ${n === "usage" ? 'min="0" max="100"' : ""}>`;
    return `<div class="field ${type === "textarea" || type === "file" ? "full" : ""}"><label for="f-${n}">${label}${req ? " *" : ""}</label>${control}</div>`;
}

function openModal(key, id) {
    const cfg = configs[key];
    if (!cfg) return;
    const item = id ? db[key].find((x) => String(x.id) === String(id)) : null;
    let fields = cfg.fields;
    if (item && key === "datasets")
        fields = fields.filter((f) => f[0] !== "file" && (f[0] !== "fileName" || !item.hasData));
    $("#modal-title").textContent = item ? cfg.editTitle : cfg.title;
    $("#modal-fields").innerHTML =
        (cfg.note ? `<div class="demo-note">${cfg.note}</div>` : "") +
        `<div class="form-grid">${fields.map((f) => fieldHtml(f, item)).join("")}</div>`;
    $("#modal-error").hidden = true;
    $("#save-btn").textContent = "Save";
    $("#save-btn").disabled = false;
    $("#modal").dataset.kind = key;
    $("#modal").dataset.editId = item ? item.id : "";
    $("#modal").showModal();
}

function closeModal() {
    if (modalSaving) return;
    const modal = $("#modal");
    if (modal.open) modal.close();
    $("#modal-form").reset();
}

function toast(msg, type = "success") {
    const t = $("#toast");
    t.textContent = msg;
    t.className = "show " + (type === "error" ? "error" : "");
    clearTimeout(toast.timer);
    toast.timer = setTimeout(() => t.classList.remove("show"), type === "error" ? 6000 : 3500);
}

/** Saves the modal form. Throws on failure so the modal stays open with the error. */
async function saveItem(key, editId, form) {
    const obj = Object.fromEntries(
        [...new FormData(form).entries()].filter(([, v]) => typeof v === "string").map(([k, v]) => [k, v.trim()]),
    );
    const editing = Boolean(editId);

    if (key === "projects") {
        const body = JSON.stringify({
            title: obj.title,
            description: obj.description || "",
            team: obj.team || "",
            status: obj.status || "Planning",
        });
        const saved = await api(editing ? `/projects/${editId}` : "/projects", {
            method: editing ? "PUT" : "POST",
            body,
        });
        replaceOrAdd("projects", saved);
        return editing ? "Project updated in the database." : "Project saved to the database.";
    }
    if (key === "datasets") {
        const file = form.querySelector('input[type="file"]')?.files[0];
        let saved;
        if (file) {
            if (file.size > 2 * 1024 * 1024) throw new Error("The CSV file is larger than 2 MB.");
            const data = new FormData();
            data.append("file", file);
            data.append("name", obj.name);
            data.append("description", obj.description || "");
            data.append("owner", obj.owner || "");
            saved = await api("/datasets/upload", { method: "POST", body: data }, 60000);
        } else {
            const existing = editing ? db.datasets.find((d) => String(d.id) === String(editId)) : null;
            // status null = new records become "Metadata only"; edited records keep their current status.
            const body = JSON.stringify({
                name: obj.name,
                description: obj.description || "",
                owner: obj.owner || "",
                fileName: obj.fileName ?? existing?.fileName ?? "",
                status: null,
            });
            saved = await api(editing ? `/datasets/${editId}` : "/datasets", {
                method: editing ? "PUT" : "POST",
                body,
            });
        }
        replaceOrAdd("datasets", saved);
        if (saved.hasData) ml.datasetId = saved.id;
        return file
            ? `CSV uploaded: ${saved.rowCount} rows, ${saved.columns.length} columns.`
            : editing
              ? "Dataset updated in the database."
              : "Dataset saved to the database.";
    }
    if (key === "training") {
        const saved = await api("/training", {
            method: "POST",
            body: JSON.stringify({ model: obj.model, dataset: obj.dataset, parameters: obj.parameters || "" }),
        });
        replaceOrAdd("training", saved);
        return "External job recorded (no training was run).";
    }

    // Demo-only sections, stored in this browser.
    obj.id = Math.max(0, ...db[key].map((x) => Number(x.id) || 0)) + 1;
    if (key === "resources") {
        obj.usage = Number(obj.usage);
        if (!Number.isFinite(obj.usage) || obj.usage < 0 || obj.usage > 100)
            throw new Error("Utilization must be between 0 and 100.");
    }
    if (!obj.status) obj.status = "Ready";
    db[key].unshift(obj);
    save();
    return "Item created (demo data, saved in this browser).";
}

function replaceOrAdd(key, item) {
    const i = db[key].findIndex((x) => x.id === item.id);
    if (i >= 0) db[key][i] = item;
    else db[key].unshift(item);
}

async function deleteItem(key, id) {
    if (!confirm("Delete this item?")) return;
    if (BACKEND_KINDS.includes(key)) {
        const path =
            { projects: "/projects/", datasets: "/datasets/", training: "/training/" }[key] + encodeURIComponent(id);
        try {
            await api(path, { method: "DELETE" });
        } catch (error) {
            // A 404 means it is already gone from the database, so remove it from the screen too.
            if (!/not found/i.test(error.message)) {
                toast("Could not delete: " + error.message, "error");
                return;
            }
        }
        db[key] = db[key].filter((x) => String(x.id) !== String(id));
        if (key === "datasets") delete ml.previews[id];
        render();
        toast("Deleted from the database.");
        return;
    }
    db[key] = db[key].filter((x) => String(x.id) !== String(id));
    save();
    render();
    toast("Item deleted (demo data).");
}

/* ===================== Event listeners (each registered exactly once) ===================== */

$("#nav").addEventListener("click", (e) => {
    const b = e.target.closest("[data-view]");
    if (b) {
        view = b.dataset.view;
        render();
    }
});

document.addEventListener("click", (e) => {
    const t = e.target;
    const go = t.closest("[data-go]");
    if (go) {
        view = go.dataset.go;
        render();
        return;
    }
    const add = t.closest("[data-add]");
    if (add) {
        openModal(add.dataset.add);
        return;
    }
    const edit = t.closest("[data-edit]");
    if (edit) {
        openModal(edit.dataset.edit, edit.dataset.id);
        return;
    }
    const del = t.closest("[data-delete]");
    if (del) {
        deleteItem(del.dataset.delete, del.dataset.id);
        return;
    }
    if (t.closest("[data-close-modal]")) {
        closeModal();
        return;
    }
    if (t.closest("#ai-new-chat")) {
        newChat();
        return;
    }
    const open = t.closest("[data-chat-open]");
    if (open) {
        chatState.activeId = open.dataset.chatOpen;
        saveChats();
        renderChat();
        return;
    }
    const delChat = t.closest("[data-chat-delete]");
    if (delChat) {
        if (confirm("Delete this conversation?")) deleteChat(delChat.dataset.chatDelete);
        return;
    }
    const action = t.closest("[data-action]");
    if (!action) return;
    if (action.dataset.action === "retry") {
        backend.checked = false;
        renderBackendStatus();
        refreshBackend();
    }
    if (action.dataset.action === "load-sample") loadSampleDataset();
    if (action.dataset.action === "view-job") {
        ml.selectedJobId = Number(action.dataset.id);
        view = "training";
        render();
        $("#ml-result")?.scrollIntoView({ behavior: "smooth" });
    }
});

$("#main-action").addEventListener("click", () => {
    if (view === "training") {
        $("#ml-panel")?.scrollIntoView({ behavior: "smooth" });
        return;
    }
    openModal(
        {
            overview: "projects",
            projects: "projects",
            datasets: "datasets",
            experiments: "experiments",
            collaboration: "collaboration",
            users: "users",
            resources: "resources",
        }[view],
    );
});

$("#role-select").addEventListener("change", (e) => {
    role = e.target.value;
    view = "overview";
    render();
    toast(role === "admin" ? "Admin demo view enabled (not a login)." : "Researcher demo view enabled.");
});

$("#modal-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const form = e.currentTarget;
    if (modalSaving || !form.reportValidity()) return;
    const modal = $("#modal");
    const errorBox = $("#modal-error");
    errorBox.hidden = true;
    modalSaving = true;
    $("#save-btn").disabled = true;
    $("#save-btn").textContent = "Saving…";
    $$("#modal [data-close-modal]").forEach((b) => (b.disabled = true));
    try {
        const message = await saveItem(modal.dataset.kind, modal.dataset.editId, form);
        modalSaving = false;
        closeModal();
        render();
        toast(message);
    } catch (error) {
        errorBox.textContent = error.message;
        errorBox.hidden = false;
    } finally {
        modalSaving = false;
        $("#save-btn").disabled = false;
        $("#save-btn").textContent = "Save";
        $$("#modal [data-close-modal]").forEach((b) => (b.disabled = false));
    }
});

// Esc key: close like Cancel, but not while a save is in progress.
$("#modal").addEventListener("cancel", (e) => {
    e.preventDefault();
    closeModal();
});

document.addEventListener("submit", (e) => {
    const id = e.target.id;
    if (id === "profile-form") {
        e.preventDefault();
        toast("Demo only: profile changes are not saved.");
    }
    if (id === "ai-chat-form") {
        e.preventDefault();
        chatDraft = $("#ai-prompt").value;
        sendChat();
    }
    if (id === "ml-form") {
        e.preventDefault();
        startTraining();
    }
    if (id === "predict-form") {
        e.preventDefault();
        predict(e.target);
    }
});

document.addEventListener("input", (e) => {
    if (e.target.id === "ai-prompt") chatDraft = e.target.value;
});

document.addEventListener("keydown", (e) => {
    if (e.target.id === "ai-prompt" && e.key === "Enter" && !e.shiftKey && !e.isComposing) {
        e.preventDefault();
        $("#ai-chat-form")?.requestSubmit();
    }
});

document.addEventListener("change", (e) => {
    const t = e.target;
    if (t.id === "ml-dataset") {
        ml.datasetId = Number(t.value);
        ml.prediction = null;
        refreshTrainingViews(true);
        loadPreview(ml.datasetId).then(() => {
            const p = ml.previews[ml.datasetId];
            if (p && !p.numericColumns.includes(ml.target)) {
                ml.target = p.numericColumns[p.numericColumns.length - 1] || null;
                ml.features = new Set(p.numericColumns.filter((c) => c !== ml.target));
            }
            refreshTrainingViews(true);
        });
    } else if (t.id === "ml-target") {
        ml.target = t.value;
        ml.features.delete(ml.target);
        if (!ml.features.size)
            ml.features = new Set(ml.previews[ml.datasetId].numericColumns.filter((c) => c !== ml.target));
        refreshTrainingViews(true);
    } else if (t.id === "ml-test") {
        ml.testRatio = Number(t.value);
    } else if (t.id === "ml-seed") {
        ml.seed = Number(t.value);
    } else if (t.dataset && t.dataset.feature !== undefined) {
        if (t.checked) ml.features.add(t.dataset.feature);
        else ml.features.delete(t.dataset.feature);
    } else if (t.id === "f-file" && t.files[0]) {
        const name = $("#f-name");
        if (name && !name.value.trim()) name.value = t.files[0].name.replace(/\.csv$/i, "");
    }
});

load();
