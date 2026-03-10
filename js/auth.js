document.getElementById("loginForm").addEventListener("submit", async function (e) {
    e.preventDefault();

    const username = document.getElementById("username").value.trim();
    const password = document.getElementById("password").value.trim();
    const role = document.getElementById("role").value;

    if (!username || !password) {
        alert("Enter username and password.");
        return;
    }

    try {
        const { token, user } = await Api.login({ username, password });

        if (role === "student" && user.role !== "student") {
            alert("This account is not a student account.");
            return;
        }

        if (role === "admin" && !(user.role === "teacher" || user.role === "admin")) {
            alert("This account is not a teacher/admin account.");
            return;
        }

        AppStore.setAuth(user, token);
        window.location.href = role === "student" ? "dashboard2.html" : "admin_dashboard.html";
    } catch (err) {
        const msg = String(err.message || "");
        if (msg.toLowerCase().includes("invalid credentials")) {
            alert("Account not found. Register from the main login page first.");
            return;
        }
        alert(msg || "Login failed.");
    }
});
