const chatContainer = document.getElementById('chat-container');
const userInput = document.getElementById('user-input');
const sendBtn = document.getElementById('send-btn');

function appendMessage(text, sender) {
    const messageDiv = document.createElement('div');
    messageDiv.classList.add('message', sender);
    messageDiv.innerHTML = `<p>${text}</p>`;
    chatContainer.appendChild(messageDiv);
    chatContainer.scrollTop = chatContainer.scrollHeight;
}

sendBtn.addEventListener('click', () => {
    const text = userInput.value.trim();
    if (text) {
        // 1. Screen par user ka message dikhao
        appendMessage(text, 'user');
        userInput.value = '';

        // 2. JADOO: Kotlin/Android system ko message bhejo!
        if (window.AndroidBridge) {
            window.AndroidBridge.sendToSaara(text);
        } else {
            console.log("Bridge not found. Not running inside the app.");
            // Testing on PC fallback
            setTimeout(() => appendMessage("Testing mode: Android bridge not connected.", 'saara'), 1000);
        }
    }
});

// Kotlin is function ko call karega jab Vercel se response aayega
window.receiveFromSaara = function(text) {
    appendMessage(text, 'saara');
};

// Enter dabane par bhi message send ho
userInput.addEventListener('keypress', function (e) {
    if (e.key === 'Enter') {
        sendBtn.click();
    }
});
