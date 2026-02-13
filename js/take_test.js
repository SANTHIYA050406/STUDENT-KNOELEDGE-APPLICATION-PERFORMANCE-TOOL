
window.onload = function () {
    loadTest();
};

function loadTest() {
    const testId = localStorage.getItem("activeTest");
    const tests = Storage.getTests();
    const test = tests.find(t => t.id == testId);

    const container = document.getElementById("testContainer");

    if (!test) {
        container.innerHTML = "<p>Test not found</p>";
        return;
    }

    if (!test.questions || test.questions.length === 0) {
        container.innerHTML = "<p>No questions added to this test.</p>";
        return;
    }

    container.innerHTML = `<h3>${test.title}</h3>`;

    test.questions.forEach((q, index) => {
        const div = document.createElement("div");

        div.innerHTML = `
            <p><b>Q${index + 1}:</b> ${q.text}</p>
            ${q.options.map((option, i) => `
                <label>
                    <input type="radio" name="q${index}" value="${String.fromCharCode(65+i)}">
                    ${option}
                </label><br>
            `).join("")}
            <hr>
        `;

        container.appendChild(div);
    });
}

function submitTest() {
    const user = Storage.getCurrentUser();
    const testId = localStorage.getItem("activeTest");
    const tests = Storage.getTests();
    const test = tests.find(t => t.id == testId);

    if (!test) {
        alert("Test not found");
        return;
    }

    let score = 0;

    test.questions.forEach((q, index) => {
        const selected = document.querySelector(`input[name="q${index}"]:checked`);

        if (selected && selected.value === q.answer) {
            score++;
        }
    });

    const results = Storage.getResults();
    const now = new Date();

    results.push({
        id: Date.now(),
        studentId: user.id,
        studentName: user.username,
        testId: test.id,
        score: score,
        total: test.questions.length,
        date: now.toLocaleDateString() + " " + now.toLocaleTimeString()
    });

    Storage.saveResults(results);

    alert(`Test submitted!\nScore: ${score}/${test.questions.length}`);
    window.location.href = "dashboard.html";
}

function logout() {
    localStorage.removeItem("currentUser");
    window.location.href = "index.html";
}
