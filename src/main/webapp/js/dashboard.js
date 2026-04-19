document.addEventListener("DOMContentLoaded", async () => {
    let user = AppStore.getState().user || JSON.parse(localStorage.getItem("currentUser") || "null");
    if (!user) {
        window.location.href = "index2.html";
        return;
    }

    if (user.role !== "student") {
        window.location.href = "admin_dashboard.html";
        return;
    }

    try {
        const meRes = await Api.me();
        if (meRes && meRes.user) {
            user = meRes.user;
            AppStore.setAuth(user, localStorage.getItem("token"));
        }
    } catch (err) {
        const msg = String(err.message || "").toLowerCase();
        if (msg.includes("token") || msg.includes("authorization")) {
            AppStore.clearAuth();
            window.location.href = "index2.html";
            return;
        }
        alert(err.message || "Failed to validate session.");
        return;
    }

    let tests = [];
    let results = [];

    try {
        const [testsRes, resultsRes] = await Promise.all([Api.getTests(), Api.getResults()]);
        tests = testsRes.tests || [];
        results = resultsRes.results || [];
        AppStore.setTests(tests);
        AppStore.setResults(results);
    } catch (err) {
        alert(err.message || "Failed to load dashboard data");
        return;
    }

    const userResults = results.filter(r => {
        if (r.studentId && user.id) return Number(r.studentId) === Number(user.id);
        if (r.studentUsername) return r.studentUsername === user.username;
        return r.username === user.username;
    });

    const totalTests = tests.length;
    const attempted = userResults.length;
    const avgScore = attempted
        ? Math.round(userResults.reduce((s, r) => s + Number(r.score || 0), 0) / attempted)
        : 0;

    const historyRows = userResults.length
        ? userResults.map(r => {
            const test = tests.find(t => Number(t.id) === Number(r.testId));
            const dateText = r.submittedAt ? new Date(r.submittedAt).toLocaleDateString() : (r.date || "-");
            return `<tr><td>${test ? test.title : (r.testTitle || "Test")}</td><td>${r.score}%</td><td>${dateText}</td></tr>`;
        }).join("")
        : '<tr><td colspan="3">No tests attempted yet.</td></tr>';

    const topSection = document.getElementById("topSection");
    if (topSection) {
        topSection.innerHTML = `
            <div class="card stat-card">
                <h2>${totalTests}</h2>
                <p>Total Tests</p>
            </div>

            <div class="card stat-card">
                <h2>${attempted}</h2>
                <p>Tests Attempted</p>
            </div>

            <div class="card stat-card">
                <h2>${avgScore}%</h2>
                <p>Avg. Score</p>
            </div>

            <div class="card result-card">
                <h3>History</h3>
                <table>
                    <thead><tr><th>Test</th><th>Score</th><th>Date</th></tr></thead>
                    <tbody>${historyRows}</tbody>
                </table>
            </div>
        `;
    }

    const testsSection = document.getElementById("testsSection");
    if (testsSection) {
        testsSection.innerHTML = tests.map(test => `
            <div class="card test-card">
                <h3>${test.title}</h3>
                <p><strong>Subject:</strong> ${test.subject}</p>
                <button onclick="startTest(${test.id})">Take Test</button>
            </div>
        `).join("");
    }

    const chartCanvas = document.getElementById("performanceChart");
    if (chartCanvas) {
        const fresh = chartCanvas.cloneNode(false);
        chartCanvas.parentNode.replaceChild(fresh, chartCanvas);

        if (userResults.length) {
            const ctx = fresh.getContext("2d");
            new Chart(ctx, {
                type: "line",
                data: {
                    labels: userResults.map(r => r.date || (r.submittedAt ? new Date(r.submittedAt).toLocaleDateString() : "-")),
                    datasets: [{
                        label: "Score (%)",
                        data: userResults.map(r => Number(r.score || 0)),
                        borderColor: "#667eea",
                        backgroundColor: "rgba(102, 126, 234, 0.2)",
                        tension: 0.3,
                        fill: true
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    scales: { y: { beginAtZero: true, max: 100 } }
                }
            });
        }
    }
});

function startTest(id) {
    localStorage.setItem("selectedTestId", id);
    window.location.href = "take_test.html";
}

function logout() {
    AppStore.clearAuth();
    window.location.href = "index.html";
}
