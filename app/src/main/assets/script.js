// State Variables
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
const centralGlobe = document.getElementById('centralGlobe');
const thinkingIndicator = document.getElementById('thinkingIndicator');
const voiceWaveform = document.getElementById('voiceWaveform');
const appContainer = document.querySelector('.app-container');

/* ==========================================
   1. Dynamic Viewport Height (WebView / Keyboard Fix)
   ========================================== */
function updateAppHeight() {
    const vh = window.visualViewport ? window.visualViewport.height : window.innerHeight;
    document.documentElement.style.setProperty('--app-height', `${vh}px`);
}

// Initial Set & Resize Listeners
updateAppHeight();
window.addEventListener('resize', updateAppHeight);
window.addEventListener('orientationchange', updateAppHeight);

if (window.visualViewport) {
    window.visualViewport.addEventListener('resize', () => {
        updateAppHeight();
        scrollToBottom();
    });
}

// Keyboard Active Listeners
if (userInput && appContainer) {
    userInput.addEventListener('focus', () => {
        appContainer.classList.add('keyboard-active');
        setTimeout(() => {
            updateAppHeight();
            scrollToBottom();
        }, 200);
    });

    userInput.addEventListener('blur', () => {
        appContainer.classList.remove('keyboard-active');
        setTimeout(updateAppHeight, 200);
    });
}

/* ==========================================
   2. Web Speech Recognition Setup
   ========================================== */
if ('webkitSpeechRecognition' in window || 'SpeechRecognition' in window) {
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    recognition = new SpeechRecognition();
    recognition.continuous = false;
    recognition.interimResults = false;
    recognition.lang = 'hi-IN'; // Hinglish / Hindi Support

    recognition.onstart = () => {
        isListening = true;
        if (micBtn) micBtn.classList.add('listening');
        setOrbState('speaking');
        if (voiceWaveform) voiceWaveform.classList.remove('hidden');
    };

    recognition.onresult = (event) => {
        const transcript = event.results[0][0].transcript;
        if (userInput) userInput.value = transcript;
        sendMessage();
    };

    recognition.onerror = () => {
        resetMicState();
    };

    recognition.onend = () => {
        resetMicState();
    };
}

/* ==========================================
   3. Event Listeners
   ========================================== */
if (sendBtn) sendBtn.addEventListener('click', sendMessage);

if (userInput) {
    userInput.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') sendMessage();
    });
}

if (micBtn) micBtn.addEventListener('click', toggleMic);
if (liveModeBtn) liveModeBtn.addEventListener('click', toggleLiveVoice);

/* ==========================================
   4. Mic & Voice Controls
   ========================================== */
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
    if (micBtn) micBtn.classList.remove('listening');
    setOrbState('idle');
    if (voiceWaveform) voiceWaveform.classList.add('hidden');
}

function toggleLiveVoice() {
    isLiveMode = !isLiveMode;
    if (liveModeBtn) liveModeBtn.classList.toggle('active', isLiveMode);
    appendAssistantMessage(
        isLiveMode 
            ? "Live Voice Mode ON ho gaya hai Sir. Ab main aapko har jawaab bol kar doongi." 
            : "Live Voice Mode OFF kar diya hai Sir."
    );
}

function triggerQuickAction(text) {
    if (userInput) userInput.value = text;
    sendMessage();
}

/* ==========================================
   5. Messaging & Android Bridge Interactions
   ========================================== */
function sendMessage() {
    const text = userInput ? userInput.value.trim() : '';
    if (!text) return;

    appendUserMessage(text);
    if (userInput) userInput.value = '';

    // UI States Sync
    setOrbState('thinking');
    if (thinkingIndicator) thinkingIndicator.classList.remove('hidden');
    scrollToBottom();

    const lowerText = text.toLowerCase();

    // A. SCREEN CAST INTENT
    if (lowerText.includes('cast') || lowerText.includes('screen share') || lowerText.includes('mirror')) {
        if (window.AndroidBridge && typeof window.AndroidBridge.openScreenCast === 'function') {
            window.AndroidBridge.openScreenCast();
            receiveFromSaara(JSON.stringify({ reply: "Screen Cast settings khol rahi hoon Sir..." }));
            return;
        }
    }

    // B. WHATSAPP INTENT ("Rahul ko WhatsApp karo Hello")
    if (lowerText.includes('whatsapp') || lowerText.includes('whats app') || lowerText.includes('what\'s app')) {
        const extracted = extractTargetAndMessage(text, ['whatsapp karo', 'whatsapp text', 'whatsapp message', 'whatsapp par', 'whatsapp']);
        if (window.AndroidBridge && typeof window.AndroidBridge.sendWhatsApp === 'function') {
            window.AndroidBridge.sendWhatsApp(extracted.target, extracted.message);
            receiveFromSaara(JSON.stringify({ reply: `${extracted.target} ko WhatsApp message bhej rahi hoon...` }));
            return;
        }
    }

    // C. PHONE CALL INTENT ("Rahul ko call karo" / "Call 9876543210")
    if (lowerText.includes('call karo') || lowerText.includes('call lagao') || lowerText.includes('phone karo') || lowerText.startsWith('call ')) {
        let target = text
            .replace(/call karo/gi, '')
            .replace(/call lagao/gi, '')
            .replace(/phone karo/gi, '')
            .replace(/^call/gi, '')
            .replace(/\bko\b/gi, '')
            .replace(/\bpar\b/gi, '')
            .trim();

        if (window.AndroidBridge && typeof window.AndroidBridge.makeCall === 'function') {
            window.AndroidBridge.makeCall(target);
            receiveFromSaara(JSON.stringify({ reply: `${target} ko call mila rahi hoon Sir...` }));
            return;
        }
    }

    // D. SMS INTENT ("Rahul ko message karo Aaj sham aana")
    if (lowerText.includes('message karo') || lowerText.includes('sms karo') || lowerText.includes('text karo')) {
        const extracted = extractTargetAndMessage(text, ['message karo', 'sms karo', 'text karo', 'message', 'sms']);
        if (window.AndroidBridge && typeof window.AndroidBridge.sendSMS === 'function') {
            window.AndroidBridge.sendSMS(extracted.target, extracted.message);
            receiveFromSaara(JSON.stringify({ reply: `${extracted.target} ko SMS bhej rahi hoon...` }));
            return;
        }
    }

    // E. APP OPENING INTENT ("YouTube kholo" / "Open Instagram")
    if (lowerText.includes('kholo') || lowerText.includes('open') || lowerText.includes('khol do')) {
        let appName = text
            .replace(/kholo/gi, '')
            .replace(/open/gi, '')
            .replace(/khol do/gi, '')
            .replace(/app/gi, '')
            .trim();

        if (appName && window.AndroidBridge && typeof window.AndroidBridge.openApp === 'function') {
            window.AndroidBridge.openApp(appName);
            receiveFromSaara(JSON.stringify({ reply: `${appName} khol rahi hoon Sir...` }));
            return;
        }
    }

    // F. DEFAULT SERVER API FALLBACK
    if (window.AndroidBridge && typeof window.AndroidBridge.sendToSaara === 'function') {
        window.AndroidBridge.sendToSaara(text, isLiveMode);
    } else {
        // Local Fallback for Browser Testing outside Android WebView
        setTimeout(() => {
            receiveFromSaara(JSON.stringify({
                reply: "Arey Sir! Saara active hai. Bataiye Screen Cast, Calls, WhatsApp ya Application kholna hai?",
                audio: null
            }));
        }, 1200);
    }
}

// Callback invoked by Native Kotlin (`MainActivity.kt`)
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

/* ==========================================
   6. UI Rendering & Helpers
   ========================================== */
function appendUserMessage(text) {
    const msgDiv = document.createElement('div');
    msgDiv.className = 'message user-msg';
    msgDiv.innerHTML = `<div class="msg-bubble">${escapeHtml(text)}</div>`;
    chatFeed.appendChild(msgDiv);
}

function appendAssistantMessage(text) {
    const msgDiv = document.createElement('div');
    msgDiv.className = 'message assistant-msg';
    
    // Safely escape quotes for TTS onClick attribute
    const safeSpeechText = text.replace(/'/g, "\\'").replace(/"/g, "&quot;").replace(/\n/g, ' ');

    msgDiv.innerHTML = `
        <div class="avatar"><i class="fa-solid fa-sparkles"></i></div>
        <div class="msg-bubble">
            <div class="msg-text">${escapeHtml(text)}</div>
            <div class="msg-actions">
                <button class="speaker-btn" onclick="speakText('${safeSpeechText}')">
                    <i class="fa-solid fa-volume-high"></i>
                </button>
            </div>
        </div>
    `;
    chatFeed.appendChild(msgDiv);
}

// Helper to extract Target Contact Name/Number and Message Body from natural voice input
function extractTargetAndMessage(text, keywords) {
    let cleanText = text;
    
    keywords.forEach(kw => {
        const regex = new RegExp(kw, 'gi');
        cleanText = cleanText.replace(regex, '|');
    });

    let parts = cleanText.split('|').map(p => p.trim()).filter(Boolean);
    let target = "";
    let message = "Hello";

    if (parts.length >= 2) {
        target = parts[0].replace(/\bko\b/gi, '').replace(/\bpar\b/gi, '').trim();
        message = parts.slice(1).join(' ').trim();
    } else if (parts.length === 1) {
        let subParts = parts[0].split(/\bko\b|\bpar\b/gi);
        if (subParts.length >= 2) {
            target = subParts[0].trim();
            message = subParts.slice(1).join(' ').trim();
        } else {
            target = parts[0].trim();
        }
    }

    return { target: target || "Contact", message: message || "Hello" };
}

// Sync States for both Header Orb & Sci-Fi Central Globe
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
        window.speechSynthesis.cancel(); // Stop active speech
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
    if (chatFeed) {
        chatFeed.scrollTop = chatFeed.scrollHeight;
    }
}
