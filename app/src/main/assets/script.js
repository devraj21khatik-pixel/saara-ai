let isLiveMode = false;
let isListening = false;
let recognition = null;

// DOM Elements
const chatFeed = document.getElementById('chatFeed');
const userInput = document.getElementById('userInput');
const sendBtn = document.getElementById('sendBtn');
const micBtn = document.getElementById('micBtn');
const liveModeBtn = document.getElementById('liveModeBtn');
const aiOrb = document.getElementById('aiOrb');
const thinkingIndicator = document.getElementById('thinkingIndicator');
const voiceWaveform = document.getElementById('voiceWaveform');

// Initialize Web Speech Recognition
if ('webkitSpeechRecognition' in window || 'SpeechRecognition' in window) {
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    recognition = new SpeechRecognition();
    recognition.continuous = false;
    recognition.interimResults = false;
    recognition.lang = 'hi-IN'; // Hinglish / Hindi Support

    recognition.onstart = () => {
        isListening = true;
        micBtn.classList.add('listening');
        setOrbState('speaking');
        voiceWaveform.classList.remove('hidden');
    };

    recognition.onresult = (event) => {
        const transcript = event.results[0][0].transcript;
        userInput.value = transcript;
        sendMessage();
    };

    recognition.onerror = () => {
        resetMicState();
    };

    recognition.onend = () => {
        resetMicState();
    };
}

// Event Listeners
sendBtn.addEventListener('click', sendMessage);
userInput.addEventListener('keypress', (e) => {
    if (e.key === 'Enter') sendMessage();
});

micBtn.addEventListener('click', toggleMic);
liveModeBtn.addEventListener('click', toggleLiveVoice);

function toggleMic() {
    if (!recognition) {
        alert("Voice recognition not supported in this WebView.");
        return;
    }
    if (isListening) {
        recognition.stop();
    } else {
        recognition.start();
    }
}

function resetMicState() {
    isListening = false;
    micBtn.classList.remove('listening');
    setOrbState('idle');
    voiceWaveform.classList.add('hidden');
}

function toggleLiveVoice() {
    isLiveMode = !isLiveMode;
    liveModeBtn.classList.toggle('active', isLiveMode);
    appendAssistantMessage(
        isLiveMode 
            ? "Live Voice Mode ON ho gaya hai Sir. Ab main aapko har jawaab bol kar doongi." 
            : "Live Voice Mode OFF kar diya hai Sir."
    );
}

function triggerQuickAction(text) {
    userInput.value = text;
    sendMessage();
}

function sendMessage() {
    const text = userInput.value.trim();
    if (!text) return;

    appendUserMessage(text);
    userInput.value = '';

    // Set UI States
    setOrbState('thinking');
    thinkingIndicator.classList.remove('hidden');
    scrollToBottom();

    // System Intent Auto Detection (Screen Cast / App launch)
    const lowerText = text.toLowerCase();
    if (lowerText.includes('cast') || lowerText.includes('screen share') || lowerText.includes('mirror')) {
        if (window.AndroidBridge && typeof window.AndroidBridge.openScreenCast === 'function') {
            window.AndroidBridge.openScreenCast();
        }
    }

    // Call Native Android Bridge
    if (window.AndroidBridge && typeof window.AndroidBridge.sendToSaara === 'function') {
        window.AndroidBridge.sendToSaara(text, isLiveMode);
    } else {
        // Local Fallback testing
        setTimeout(() => {
            receiveFromSaara(JSON.stringify({
                reply: "Arey Sir! Saara active hai. Bataiye Screen Cast kholna hai ya aur koi madad karun?",
                audio: null
            }));
        }, 1200);
    }
}

// Callback Function called from Kotlin (`MainActivity.kt`)
function receiveFromSaara(responseString) {
    thinkingIndicator.classList.add('hidden');
    setOrbState('idle');

    try {
        const data = typeof responseString === 'string' ? JSON.parse(responseString) : responseString;
        
        appendAssistantMessage(data.reply);

        // Play Server Audio if present
        if (data.audio) {
            playAudio(data.audio, data.mimeType || 'audio/wav');
        }
    } catch (e) {
        appendAssistantMessage(responseString);
    }

    scrollToBottom();
}

function appendUserMessage(text) {
    const msgDiv = document.createElement('div');
    msgDiv.className = 'message user-msg';
    msgDiv.innerHTML = `<div class="msg-bubble">${text}</div>`;
    chatFeed.appendChild(msgDiv);
}

function appendAssistantMessage(text) {
    const msgDiv = document.createElement('div');
    msgDiv.className = 'message assistant-msg';
    msgDiv.innerHTML = `
        <div class="avatar"><i class="fa-solid fa-sparkles"></i></div>
        <div class="msg-bubble">
            <div class="msg-text">${text}</div>
            <div class="msg-actions">
                <button class="speaker-btn" onclick="speakText('${text.replace(/'/g, "\\'")}')"><i class="fa-solid fa-volume-high"></i></button>
            </div>
        </div>
    `;
    chatFeed.appendChild(msgDiv);
}

function setOrbState(state) {
    aiOrb.className = `ai-orb ${state}`;
}

function playAudio(base64Data, mimeType) {
    setOrbState('speaking');
    voiceWaveform.classList.remove('hidden');
    
    const audio = new Audio(`data:${mimeType};base64,${base64Data}`);
    audio.play().catch(err => console.log("Audio play error:", err));
    
    audio.onended = () => {
        setOrbState('idle');
        voiceWaveform.classList.add('hidden');
    };
}

function speakText(text) {
    if ('speechSynthesis' in window) {
        const utterance = new SpeechSynthesisUtterance(text);
        utterance.lang = 'hi-IN';
        window.speechSynthesis.speak(utterance);
    }
}

function scrollToBottom() {
    chatFeed.scrollTop = chatFeed.scrollHeight;
}
