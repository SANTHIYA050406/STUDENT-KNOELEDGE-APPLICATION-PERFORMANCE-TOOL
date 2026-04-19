const listeners = new Set();

const state = {
    user: JSON.parse(localStorage.getItem("currentUser") || "null"),
    token: localStorage.getItem("token") || null,
    tests: [],
    results: []
};

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
