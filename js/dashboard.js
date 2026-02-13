document.addEventListener("DOMContentLoaded", () => {

    // ===== Get Current User =====
    const user = Storage.getCurrentUser();
    if (!user) {
        window.location.href = "index.html";
        return;
    }

    const navAction = document.getElementById("navAction");

    if (navAction) {
        // ===== ROLE CHECK =====
        if (user.role === "admin") {
            navAction.textContent = "Create Test";
            navAction.href = "create_test.html";

            const chartCanvas = document.getElementById("performanceChart");
            if (chartCanvas) {
                chartCanvas.style.display = "none";
            }
        } else {
            navAction.textContent = "My Dashboard";
        }
    }

    // ===== FETCH DATA =====
    const tests = Storage.getTests() || [];
    const results = Storage.getResults() || [];
    const userResults = results.filter(r => r.studentId === user.id);

    console.log("Tests:", tests);
    console.log("User:", user);

    const totalTests = tests.length;
    const attempted = userResults.length;

    let avgScore = 0;
    if (attempted > 0) {
        const total = userResults.reduce((sum, r) => sum + r.score, 0);
        avgScore = Math.round(total / attempted);
    }

    // ================= TOP SECTION =================
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
                <p>Average Score</p>
            </div>

            <div class="card result-card">
                <h3>Result History</h3>
                <table>
                    <thead>
                        <tr>
                            <th>Test ID</th>
                            <th>Score</th>
                            <th>Date</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${userResults.map(r => `
                            <tr>
                                <td>${r.testId}</td>
                                <td>${r.score}%</td>
                                <td>${r.date}</td>
                            </tr>
                        `).join("")}
                    </tbody>
                </table>
            </div>
        `;
    }

    // ================= AVAILABLE TESTS =================
    const testsSection = document.getElementById("testsSection");
    if (testsSection) {
        testsSection.innerHTML = tests.map((test, index) => `
            <div class="card test-card">
                <h3>${test.title}</h3>
                <p><strong>Subject:</strong> ${test.subject}</p>
                ${user.role === "student" ? `<button onclick="startTest(${index})">Take Test</button>` : ""}
            </div>
        `).join("");
    }

    // ================= PERFORMANCE GRAPH =================
    const chartCanvas = document.getElementById("performanceChart");
    if (user.role === "student" && userResults.length > 0 && chartCanvas) {
        const ctx = chartCanvas.getContext("2d");

        new Chart(ctx, {
            type: "line",
            data: {
                labels: userResults.map(r => r.date),
                datasets: [{
                    label: "Score (%)",
                    data: userResults.map(r => r.score),
                    borderColor: "#667eea",
                    backgroundColor: "rgba(102, 126, 234, 0.2)",
                    tension: 0.3,
                    fill: true
                }]
            },
            options:{
    responsive: true,
    maintainAspectRatio: false,
    scales:{
        y:{ beginAtZero:true, max:100 }
    }
}

        });
    }

});

// ===== START TEST =====
function startTest(index) {
    localStorage.setItem("selectedTestIndex", index);
    window.location.href = "take_test.html";
}

// ===== LOGOUT =====
function logout() {
    localStorage.removeItem("currentUser");
    window.location.href = "index.html";
}
