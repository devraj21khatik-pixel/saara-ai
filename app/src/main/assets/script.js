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
const centralGlobe = document.getElementById('centralGlobe'); // Sci-Fi Central Globe
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
        if (voiceWaveform) voiceWaveform.classList.remove('hidden');
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
    if (voiceWaveform) voiceWaveform.classList.add('hidden');
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

    // Set UI States (Orb + Central Globe sync)
    setOrbState('thinking');
    if (thinkingIndicator) thinkingIndicator.classList.remove('hidden');
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
    if (thinkingIndicator) thinkingIndicator.classList.add('hidden');

    try {
        const data = typeof responseString === 'string' ? JSON.parse(responseString) : responseString;
        
        appendAssistantMessage(data.reply);

        // Play Server Audio if present
        if (data.audio) {
            playAudio(data.audio, data.mimeType || 'audio/wav');
        } else {
            setOrbState('idle');
            if (isLiveMode) {
                speakText(data.reply);
            }
        }
    } catch (e) {
        appendAssistantMessage(responseString);
        setOrbState('idle');
    }

    scrollToBottom();
}

function appendUserMessage(text) {
    const msgDiv = document.createElement('div');
    msgDiv.className = 'message user-msg';
    msgDiv.innerHTML = `<div class="msg-bubble">${escapeHtml(text)}</div>`;
    chatFeed.appendChild(msgDiv);
}

function appendAssistantMessage(text) {
    const msgDiv = document.createElement('div');
    msgDiv.className = 'message assistant-msg';
    msgDiv.innerHTML = `
        <div class="avatar"><i class="fa-solid fa-sparkles"></i></div>
        <div class="msg-bubble">
            <div class="msg-text">${escapeHtml(text)}</div>
            <div class="msg-actions">
                <button class="speaker-btn" onclick="speakText('${text.replace(/'/g, "\\'").replace(/\n/g, ' ')}')"><i class="fa-solid fa-volume-high"></i></button>
            </div>
        </div>
    `;
    chatFeed.appendChild(msgDiv);
}

// Dual Orb & Globe State Manager
function setOrbState(state) {
    if (aiOrb) aiOrb.className = `ai-orb ${state}`;
    if (centralGlobe) centralGlobe.className = `ai-globe ${state}`;
}

function playAudio(base64Data, mimeType) {
    setOrbState('speaking');
    if (voiceWaveform) voiceWaveform.classList.remove('hidden');
    
    const audio = new Audio(`data:${mimeType};base64,${base64Data}`);
    audio.play().catch(err => console.log("Audio play error:", err));
    
    audio.onended = () => {
        setOrbState('idle');
        if (voiceWaveform) voiceWaveform.classList.add('hidden');
    };
}

function speakText(text) {
    if ('speechSynthesis' in window) {
        window.speechSynthesis.cancel(); // Stop any ongoing speech
        const utterance = new SpeechSynthesisUtterance(text);
        utterance.lang = 'hi-IN';

        utterance.onstart = () => {
            setOrbState('speaking');
            if (voiceWaveform) voiceWaveform.classList.remove('hidden');
        };

        utterance.onend = () => {
            setOrbState('idle');
            if (voiceWaveform) voiceWaveform.classList.add('hidden');
        };

        utterance.onerror = () => {
            setOrbState('idle');
            if (voiceWaveform) voiceWaveform.classList.add('hidden');
        };

        window.speechSynthesis.speak(utterance);
    }
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.innerText = text;
    return div.innerHTML;
}

function scrollToBottom() {
    chatFeed.scrollTop = chatFeed.scrollHeight;
}
