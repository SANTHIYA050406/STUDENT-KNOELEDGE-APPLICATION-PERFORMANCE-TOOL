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

console.log("All Results:", results);
console.log("Current User:", user);
console.log("User ID:", user.id);

// Check result IDs
results.forEach(r => {
    console.log("Result studentId:", r.studentId);
});

const userResults = results.filter(r => r.studentId === user.id);

console.log("Filtered User Results:", userResults);


    // ================= TOP SECTION =================
   const topSection = document.getElementById("topSection");

if (topSection) {
    topSection.innerHTML = `
        <div class="card stat-card">
            <div class="icon blue"></div>
            <h2>${totalTests}</h2>
            <p>Total Tests</p>
        </div>

        <div class="card stat-card">
            <div class="icon purple"></div>
            <h2>${attempted}</h2>
            <p>Tests Attempted</p>
        </div>

        <div class="card stat-card">
            <div class="icon orange"></div>
            <h2>${avgScore}%</h2>
            <p>Avg. Score</p>
        </div>

        <div class="card history-card">
            <h3>History</h3>
            <div class="history-list">
                ${
                    userResults.length === 0
                    ? "<p>No tests attempted yet.</p>"
                    : userResults.map(r => {
                        const test = tests.find(t => t.id === r.testId);
                        return `
                            <div class="history-item">
                                <span>${test ? test.title : "Test"}</span>
                                <span class="score">${r.score}%</span>
                            </div>
                        `;
                    }).join("")
                }
            </div>
        </div>
    `;
}
//feedback
const feedbackSection = document.getElementById("feedbackSection");

if (feedbackSection) {

    let strength = "";
    let growth = "";

    if (avgScore >= 80) {
        strength = "Excellent mastery of the subject.";
        growth = "Try applying concepts in real-world projects.";
    } 
    else if (avgScore >= 50) {
        strength = "Good understanding of fundamentals.";
        growth = "Focus on improving weak topics.";
    } 
    else {
        strength = "You are building your foundation.";
        growth = "Revise basics and retake tests.";
    }

    feedbackSection.innerHTML = `
        <div class="feedback good">
            <h3>Strength Identified</h3>
            <p>${strength}</p>
        </div>

        <div class="feedback improve">
            <h3>Growth Opportunity</h3>
            <p>${growth}</p>
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
