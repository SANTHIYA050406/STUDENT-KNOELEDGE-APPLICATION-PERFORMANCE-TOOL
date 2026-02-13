document.addEventListener("DOMContentLoaded", () => {
    const questionsContainer = document.getElementById("questionsContainer");
    const numQuestionsInput = document.getElementById("numQuestions");

    // Generate questions dynamically
    numQuestionsInput.addEventListener("change",  () => {
        const n = parseInt(numQuestionsInput.value);
        questionsContainer.innerHTML = "";

        for (let i = 1; i <= n; i++) {
            const div = document.createElement("div");
            div.innerHTML = `
                <h4>Question ${i}</h4>
                <input type="text" placeholder="Question Text" class="qText" required><br>
                <input type="text" placeholder="Option A" class="optA" required><br>
                <input type="text" placeholder="Option B" class="optB" required><br>
                <input type="text" placeholder="Option C" class="optC" required><br>
                <input type="text" placeholder="Option D" class="optD" required><br>
                <input type="text" placeholder="Correct Answer (A/B/C/D)" class="correctAns" required><hr>
            `;
            questionsContainer.appendChild(div);
        }
    });
});

function createTest() {
    const user = Storage.getCurrentUser();
    if(!user || user.role !== "admin") {
        alert("Only admins can create tests!");
        return;
    }

    const subject = document.getElementById("subject").value.trim();
    const title = document.getElementById("title").value.trim();
    const n = parseInt(document.getElementById("numQuestions").value);

    if(!subject || !title || !n) {
        alert("Please enter subject, title, and number of questions");
        return;
    }

    const qDivs = document.querySelectorAll("#questionsContainer > div");
    const questions = [];

    qDivs.forEach(div => {
        const text = div.querySelector(".qText").value.trim();
        const options = [
            div.querySelector(".optA").value.trim(),
            div.querySelector(".optB").value.trim(),
            div.querySelector(".optC").value.trim(),
            div.querySelector(".optD").value.trim()
        ];
        const answer = div.querySelector(".correctAns").value.trim().toUpperCase();

        if(!text || options.some(opt => opt==="") || !["A","B","C","D"].includes(answer)) {
            alert("Please fill all fields and enter correct answer as A/B/C/D");
            return;
        }

        questions.push({ text, options, answer });
    });

    const tests = Storage.getTests();
    tests.push({
        id: Date.now(),
        subject,
        title,
        createdBy: user.id,
        questions
    });

    Storage.saveTests(tests);
    alert("Test created successfully!");
    window.location.href = "dashboard.html";
}

function logout() {
    localStorage.removeItem("currentUser");
    window.location.href = "index.html";
}
