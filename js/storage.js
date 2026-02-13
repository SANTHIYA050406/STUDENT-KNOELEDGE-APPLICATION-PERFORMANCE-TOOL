const Storage = {

    getUsers() {
        return JSON.parse(localStorage.getItem("users")) || [];
    },

    saveUsers(users) {
        localStorage.setItem("users", JSON.stringify(users));
    },

    getCurrentUser() {
        return JSON.parse(localStorage.getItem("currentUser"));
    },

    setCurrentUser(user) {
        localStorage.setItem("currentUser", JSON.stringify(user));
    },

    getTests() {
        return JSON.parse(localStorage.getItem("tests")) || [];
    },

    saveTests(tests) {
        localStorage.setItem("tests", JSON.stringify(tests));
    },

    getResults() {
        return JSON.parse(localStorage.getItem("results")) || [];
    },

    saveResults(results) {
        localStorage.setItem("results", JSON.stringify(results));
    }

};
