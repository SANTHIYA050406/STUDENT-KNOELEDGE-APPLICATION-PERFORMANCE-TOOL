document.getElementById("loginForm").addEventListener("submit", function(e) {
    e.preventDefault();

    const username = document.getElementById("username").value.trim();
    const password = document.getElementById("password").value.trim();
    const role = document.getElementById("role").value;

    let users = Storage.getUsers();
    let user = users.find(u => u.username === username && u.role === role);

    if (!user) {
        // register new user dynamically
        user = { id: Date.now(), username, password, role };
        users.push(user);
        Storage.saveUsers(users);
    } else if (user.password !== password) {
        alert("Incorrect password");
        return;
    }

    Storage.setCurrentUser(user);
    window.location.href = "dashboard.html";
});
