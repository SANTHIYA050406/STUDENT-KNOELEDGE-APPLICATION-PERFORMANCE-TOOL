const listeners = new Set();

const state = {
    user: JSON.parse(localStorage.getItem("currentUser") || "null"),
    token: localStorage.getItem("token") || null,
    tests: [],
    results: []
};

function showGlobalError(message) {
    try {
        const text = String(message || "Unexpected error");
        const existing = document.getElementById("skapt-global-error");
        if (existing) {
            const msgEl = existing.querySelector("[data-msg]");
            if (msgEl) msgEl.textContent = text;
            return;
        }

        const banner = document.createElement("div");
        banner.id = "skapt-global-error";
        banner.style.cssText =
            "position:fixed;left:16px;right:16px;bottom:16px;z-index:99999;" +
            "background:#111827;color:#f9fafb;border:1px solid rgba(255,255,255,.12);" +
            "border-radius:14px;padding:12px 14px;box-shadow:0 20px 40px rgba(0,0,0,.35);" +
            "font-family:system-ui,-apple-system,Segoe UI,Roboto,Inter,sans-serif;font-size:14px;";

        banner.innerHTML =
            "<div style='display:flex;gap:12px;align-items:flex-start;'>" +
            "<div style='flex:1;min-width:0;'>" +
            "<div style='font-weight:700;margin-bottom:4px;'>Something went wrong</div>" +
            "<div data-msg style='opacity:.92;word-break:break-word;'></div>" +
            "<div style='opacity:.72;margin-top:8px;'>Tip: start backend (`mvn jetty:run`) then open UI at `http://127.0.0.1:8080/index.html`.</div>" +
            "</div>" +
            "<button type='button' aria-label='Close' style='background:rgba(255,255,255,.12);color:#f9fafb;border:1px solid rgba(255,255,255,.16);border-radius:10px;padding:6px 10px;cursor:pointer;'>Close</button>" +
            "</div>";

        const msgEl = banner.querySelector("[data-msg]");
        if (msgEl) msgEl.textContent = text;
        const closeBtn = banner.querySelector("button");
        if (closeBtn) closeBtn.addEventListener("click", () => banner.remove());

        (document.body || document.documentElement).appendChild(banner);
    } catch (_) {
        // ignore
    }
}

window.addEventListener("error", (e) => {
    const msg = e?.error?.message || e?.message || "Unknown error";
    showGlobalError(msg);
});

window.addEventListener("unhandledrejection", (e) => {
    const reason = e?.reason;
    const msg = reason?.message || String(reason || "Unhandled promise rejection");
    showGlobalError(msg);
});

function emit() {
    listeners.forEach((cb) => cb({ ...state }));
}

window.AppStore = {
    getState() {
        return { ...state };
    },

    subscribe(cb) {
        listeners.add(cb);
        return () => listeners.delete(cb);
    },

    setAuth(user, token) {
        state.user = user;
        state.token = token;
        localStorage.setItem("currentUser", JSON.stringify(user));
        localStorage.setItem("token", token);
        emit();
    },

    clearAuth() {
        state.user = null;
        state.token = null;
        localStorage.removeItem("currentUser");
        localStorage.removeItem("token");
        emit();
    },

    setTests(tests) {
        state.tests = tests;
        emit();
    },

    setResults(results) {
        state.results = results;
        emit();
    }
};
