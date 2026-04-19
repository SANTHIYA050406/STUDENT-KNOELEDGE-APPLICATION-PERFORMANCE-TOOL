// ===== GET USER & TEST DATA =====
const user = JSON.parse(localStorage.getItem("currentUser"));
const tests = JSON.parse(localStorage.getItem("tests")) || [];
const selectedTestId = JSON.parse(localStorage.getItem("selectedTestId"));
const results = JSON.parse(localStorage.getItem("results")) || [];

if (!user) {
    window.location.href = "index.html";
}

// Find the selected test
const currentTest = tests.find(t => t.id === selectedTestId);
const testContainer = document.getElementById("testContainer");

// ===== LOGOUT FUNCTION =====
function logout() {
    localStorage.removeItem("currentUser");
    window.location.href = "index.html";
}

// ===== RENDER QUESTIONS =====
if (currentTest) {
    currentTest.questions.forEach((q, index) => {
        const div = document.createElement("div");
        div.classList.add("question-card");
        div.style.marginBottom = "15px";
        div.style.padding = "10px";
        div.style.border = "1px solid #ccc";
        div.style.borderRadius = "6px";

        div.innerHTML = `
            <p><strong>Q${index + 1}:</strong> ${q.text}</p>
            ${q.options.map((opt, i) => `
                <label>
                    <input type="radio" name="q${index}" value="${opt}">
                    ${opt}
                </label><br>
            `).join("")}
        `;

        testContainer.appendChild(div);
    });
} else {
    testContainer.innerHTML = "<p>No test selected.</p>";
}

// ===== SUBMIT TEST =====
function submitTest() {
    if (!currentTest) return;

    let score = 0;

    currentTest.questions.forEach((q, index) => {
        const selected = document.querySelector(`input[name="q${index}"]:checked`);
        const expectedAnswer = q.correctAnswer || (q.answer ? q.options["ABCD".indexOf(q.answer)] : undefined);
        if (selected && expectedAnswer && selected.value === expectedAnswer) {
            score++;
        }
    });

    // Calculate percentage score
    const percentage = Math.round((score / currentTest.questions.length) * 100);

    // Create new result object
    const newResult = {
        testId: currentTest.id,
        username: user.username,
        score: percentage,
        date: new Date().toLocaleDateString()
    };

    // Save to localStorage
    results.push(newResult);
    localStorage.setItem("results", JSON.stringify(results));

    // Redirect to dashboard
    window.location.href = "dashboard.html";
}


