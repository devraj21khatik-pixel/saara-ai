import os
import re
import base64
import requests
from flask import Flask, render_template, request, jsonify, session

app = Flask(__name__, template_folder='.')
app.secret_key = "saara_secret_key_123"

# Environment Variables Read Karna
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", "")
OPENROUTER_API_KEY = os.getenv("OPENROUTER_API_KEY", "")
NVIDIA_API_KEY = os.getenv("NVIDIA_API_KEY", "")
BYTEZ_API_KEY = os.getenv("BYTEZ_API_KEY", "")

# Professional TTS API Keys
SARVAM_API_KEY = os.getenv("SARVAM_API_KEY", "")
ELEVENLABS_API_KEY = os.getenv("ELEVENLABS_API_KEY", "")
CARTESIA_API_KEY = os.getenv("CARTESIA_API_KEY", "")
DEEPGRAM_API_KEY = os.getenv("DEEPGRAM_API_KEY", "")

SYSTEM_PROMPT = (
    "Aapka naam Saara hai. Aap sir ki ek bahut hi pyari, caring aur smart human female assistant hain. "
    "Aapko bilkul ek asli insaan ki tarah natural Hinglish me baat karni hai. "
    "Baat karte waqt natural fillers ka use karein jaise: 'Hmm...', 'Arey Sir!', 'Acha suniye...', 'Pata hai...'. "
    "Aapko hamesha user ko keval 'sir' kehkar hi baat karni hai, Devraj nahi bolna hai. "
    "Formal ya robotic jawab mat dein, choti aur natural baatein karein. "
    "Emojis, asterisks (*), hashtags, ya formatting bilkul use mat karein kyunki aapko bolna hai."
)

def clean_text(text):
    if not text:
        return ""
    text = re.sub(r'[*#_~`|]', '', text)
    return text.strip()

# --- Professional Multi-Provider TTS Functions (with Fallback) ---

def call_sarvam_tts(text):
    if not SARVAM_API_KEY:
        raise Exception("Sarvam API key missing")
    
    url = "https://api.sarvam.ai/text-to-speech"
    headers = {
        "api-subscription-key": SARVAM_API_KEY,
        "Content-Type": "application/json"
    }
    payload = {
        "inputs": [text],
        "target_language_code": "hi-IN",
        "speaker": "meera", # Best expressive female voice profile
        "model": "bulbul:v1",
        "pace": 1.0,
        "speech_sample_rate": 22050,
        "enable_preprocessing": True
    }
    
    res = requests.post(url, headers=headers, json=payload, timeout=15)
    if res.status_code == 200:
        data = res.json()
        if "audios" in data and len(data["audios"]) > 0:
            return data["audios"][0]
    raise Exception(f"Sarvam Status {res.status_code}: {res.text}")

def call_elevenlabs_tts(text):
    if not ELEVENLABS_API_KEY:
        raise Exception("ElevenLabs API key missing")
    
    voice_id = "21m00Tcm4TlvDq8ikWAM" # Standard natural female voice ID
    url = f"https://api.elevenlabs.io/v1/text-to-speech/{voice_id}"
    headers = {
        "xi-api-key": ELEVENLABS_API_KEY,
        "Content-Type": "application/json",
        "Accept": "audio/mpeg"
    }
    payload = {
        "text": text,
        "model_id": "eleven_multilingual_v2",
        "voice_settings": {
            "stability": 0.45,
            "similarity_boost": 0.8,
            "style": 0.2
        }
    }
    
    res = requests.post(url, headers=headers, json=payload, timeout=15)
    if res.status_code == 200:
        return base64.b64encode(res.content).decode('utf-8')
    raise Exception(f"ElevenLabs Status {res.status_code}: {res.text}")

def call_cartesia_tts(text):
    if not CARTESIA_API_KEY:
        raise Exception("Cartesia API key missing")
        
    url = "https://api.cartesia.ai/tts/bytes"
    headers = {
        "X-API-Key": CARTESIA_API_KEY,
        "Cartesia-Version": "2024-06-10",
        "Content-Type": "application/json"
    }
    payload = {
        "model_id": "sonic-multilingual",
        "transcript": text,
        "voice": {
            "mode": "id",
            "id": "a0e99841-438c-4a64-b679-ae501e7d6091" # High quality female voice sample ID
        },
        "output_format": {
            "container": "wav",
            "encoding": "pcm_s16le",
            "sample_rate": 22050
        }
    }
    
    res = requests.post(url, headers=headers, json=payload, timeout=15)
    if res.status_code == 200:
        return base64.b64encode(res.content).decode('utf-8')
    raise Exception(f"Cartesia Status {res.status_code}: {res.text}")

def call_deepgram_tts(text):
    if not DEEPGRAM_API_KEY:
        raise Exception("Deepgram API key missing")
        
    url = "https://api.deepgram.com/v1/speak?model=aura-asteria-en"
    headers = {
        "Authorization": f"Token {DEEPGRAM_API_KEY}",
        "Content-Type": "application/json"
    }
    payload = {
        "text": text
    }
    
    res = requests.post(url, headers=headers, json=payload, timeout=15)
    if res.status_code == 200:
        return base64.b64encode(res.content).decode('utf-8')
    raise Exception(f"Deepgram Status {res.status_code}: {res.text}")

# --- TTS Route with Multi-Provider Fallback ---

@app.route("/tts", methods=["POST"])
def tts():
    data = request.json or {}
    text = clean_text(data.get("text", ""))
    
    if not text:
        return jsonify({"error": "No text provided"}), 400

    tts_providers = [
        ("Sarvam AI (Bulbul)", call_sarvam_tts),
        ("ElevenLabs", call_elevenlabs_tts),
        ("Cartesia", call_cartesia_tts),
        ("Deepgram", call_deepgram_tts)
    ]

    for name, func in tts_providers:
        try:
            print(f"⚡ Trying TTS Provider: {name}...")
            audio_b64 = func(text)
            if audio_b64:
                print(f"✅ Voice successfully generated via {name}!")
                return jsonify({"audio": audio_b64, "mimeType": "audio/wav"})
        except Exception as e:
            print(f"⚠️ TTS Provider [{name}] Failed: {e}. Switching to next...")

    return jsonify({"error": "All TTS services failed"}), 500

# --- LLM Provider Functions ---

def call_gemini(history):
    gemini_models = [
        "gemini-3.6-flash",
        "gemini-3.5-flash",
        "gemini-3.5-flash-lite",
        "gemini-3.1-flash-lite",
        "gemini-3-flash-preview",
        "gemini-2.5-flash-lite",
        "gemini-2.5-flash",
        "gemini-2.5-pro",
        "gemma-4-31b-it",
        "gemma-4-26b-it",
        "gemini-2.0-flash",
        "gemini-1.5-flash"
    ]
    
    gemini_contents = []
    for msg in history:
        role = "user" if msg["role"] == "user" else "model"
        gemini_contents.append({"role": role, "parts": [{"text": msg["content"]}]})

    payload = {
        "system_instruction": {"parts": [{"text": SYSTEM_PROMPT}]},
        "contents": gemini_contents,
        "generationConfig": {
            "temperature": 0.7,
            "topP": 0.95
        }
    }
    
    headers = {
        "Content-Type": "application/json",
        "X-goog-api-key": GEMINI_API_KEY
    }

    for model in gemini_models:
        url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
        try:
            res = requests.post(url, headers=headers, json=payload, timeout=8)
            if res.status_code == 200:
                print(f"✅ Success with Model: [{model}]")
                return res.json()["candidates"][0]["content"]["parts"][0]["text"]
        except Exception:
            continue

    raise Exception("All Gemini and Gemma models failed or rate limited")

def call_openrouter(history):
    url = "https://openrouter.ai/api/v1/chat/completions"
    headers = {
        "Authorization": f"Bearer {OPENROUTER_API_KEY}",
        "Content-Type": "application/json"
    }
    formatted = [{"role": "system", "content": SYSTEM_PROMPT}] + history
    payload = {
        "model": "openrouter/auto",
        "messages": formatted,
        "temperature": 0.3
    }
    res = requests.post(url, headers=headers, json=payload, timeout=8)
    if res.status_code == 200:
        return res.json()["choices"][0]["message"]["content"]
    raise Exception(f"OpenRouter Status {res.status_code}")

def call_nvidia(history):
    url = "https://integrate.api.nvidia.com/v1/chat/completions"
    headers = {
        "Authorization": f"Bearer {NVIDIA_API_KEY}",
        "Content-Type": "application/json"
    }
    formatted = [{"role": "system", "content": SYSTEM_PROMPT}] + history
    payload = {
        "model": "nvidia/nemotron-3.5-lightning-30b-a3b",
        "messages": formatted,
        "temperature": 0.3,
        "top_p": 0.95,
        "max_tokens": 2048,
        "chat_template_kwargs": {"enable_thinking": True},
        "reasoning_budget": 1024
    }
    res = requests.post(url, headers=headers, json=payload, timeout=8)
    if res.status_code == 200:
        data = res.json()
        msg = data["choices"][0]["message"]
        return msg.get("content") or msg.get("reasoning_content")
    raise Exception(f"NVIDIA Status {res.status_code}")

def call_bytez(history):
    url = "https://api.bytez.com/v1/chat/completions"
    headers = {
        "Authorization": f"Bearer {BYTEZ_API_KEY}",
        "Content-Type": "application/json"
    }
    formatted = [{"role": "system", "content": SYSTEM_PROMPT}] + history
    payload = {
        "model": "meta-llama/llama-3.3-70b-instruct",
        "messages": formatted,
        "temperature": 0.3
    }
    res = requests.post(url, headers=headers, json=payload, timeout=8)
    if res.status_code == 200:
        return res.json()["choices"][0]["message"]["content"]
    raise Exception(f"Bytez Status {res.status_code}")

# --- Web Routes ---

@app.route("/")
def home():
    session["chat_history"] = []
    return render_template("index.html")

@app.route("/chat", methods=["POST"])
def chat():
    user_message = request.json.get("message", "")
    if "chat_history" not in session:
        session["chat_history"] = []

    history = session["chat_history"]
    history.append({"role": "user", "content": user_message})

    providers = [
        ("Gemini", call_gemini),
        ("OpenRouter", call_openrouter),
        ("NVIDIA", call_nvidia),
        ("Bytez", call_bytez)
    ]

    for name, func in providers:
        try:
            raw_reply = func(history)
            reply = clean_text(raw_reply)
            if reply:
                history.append({"role": "assistant", "content": reply})
                session["chat_history"] = history
                print(f"🚀 Served by Provider: {name}")
                return jsonify({"reply": reply})
        except Exception as e:
            print(f"⚠️ {name} Failed: {e}. Switching to next Provider...")

    return jsonify({"reply": "Sabhi API services busy hain sir, thodi der baad try karein."})

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000)
