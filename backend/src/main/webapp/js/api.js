// Production (Railway): frontend + backend are served from the same origin, so use a relative `/api`.
// Local dev: keep the explicit Jetty URL to avoid cross-port issues when using a separate static server.
const API_BASE = (location.hostname === "localhost" || location.hostname === "127.0.0.1")
  ? "http://127.0.0.1:8080/api"
  : "/api";

function authHeaders() {
    const token = localStorage.getItem("token");
    return token ? { Authorization: `Bearer ${token}` } : {};
}

async function request(path, options = {}) {
    const isFormData = options.body instanceof FormData;
    const response = await fetch(`${API_BASE}${path}`, {
        headers: {
            ...(isFormData ? {} : { "Content-Type": "application/json" }),
            ...authHeaders(),
            ...(options.headers || {})
        },
        ...options
    });

    const contentType = response.headers.get("content-type") || "";
    const payload = contentType.includes("application/json") ? await response.json() : null;

    if (!response.ok) {
        throw new Error(payload?.error || `Request failed: ${response.status}`);
    }

    return payload ?? {};
}

window.Api = {
    register(data) {
        return request("/auth/register", { method: "POST", body: JSON.stringify(data) });
    },

    login(data) {
        return request("/auth/login", { method: "POST", body: JSON.stringify(data) });
    },

    me() {
        return request("/auth/me");
    },

    getTests() {
        return request("/tests");
    },

    getTest(id) {
        return request(`/tests/${id}`);
    },

    createTest(data) {
        return request("/tests", { method: "POST", body: JSON.stringify(data) });
    },

    updateTest(id, data) {
        return request(`/tests/${id}`, { method: "PUT", body: JSON.stringify(data) });
    },

    deleteTest(id) {
        return request(`/tests/${id}`, { method: "DELETE" });
    },

    getResults() {
        return request("/results");
    },

    submitResult(data) {
        return request("/results", { method: "POST", body: JSON.stringify(data) });
    },

    deleteResult(id) {
        return request(`/results/${id}`, { method: "DELETE" });
    },

    getStudents(query) {
        const q = query ? `?${new URLSearchParams(query).toString()}` : "";
        return request(`/users/students${q}`);
    },

    getAcademicDocuments(studentUsername) {
        const q = studentUsername ? `?studentUsername=${encodeURIComponent(studentUsername)}` : "";
        return request(`/documents/academics${q}`);
    },

    createAcademicDocument(data) {
        return request("/documents/academics", { method: "POST", body: JSON.stringify(data) });
    },

    getCertificationDocuments(studentUsername) {
        const q = studentUsername ? `?studentUsername=${encodeURIComponent(studentUsername)}` : "";
        return request(`/documents/certifications${q}`);
    },

    createCertificationDocument(data) {
        return request("/documents/certifications", { method: "POST", body: JSON.stringify(data) });
    },

    getCompetitionDocuments(studentUsername) {
        const q = studentUsername ? `?studentUsername=${encodeURIComponent(studentUsername)}` : "";
        return request(`/documents/competition${q}`);
    },

    createCompetitionDocument(data) {
        return request("/documents/competition", { method: "POST", body: JSON.stringify(data) });
    },

    getTopScorers(year) {
        const q = year ? `?year=${encodeURIComponent(year)}` : "";
        return request(`/reports/top-scorers${q}`);
    },

    reviewDocument(type, id, status) {
        return request(`/documents/${encodeURIComponent(type)}/${encodeURIComponent(id)}/review`, {
            method: "PUT",
            body: JSON.stringify({ status })
        });
    }
};
