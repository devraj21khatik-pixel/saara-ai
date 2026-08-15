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

# --- UPDATED KAGGLE F5-TTS VOICE ENGINE URL ---
KAGGLE_ENGINE_URL = "https://cool-bugs-kiss.loca.lt/generate"

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

# --- Kaggle F5-TTS Cloned Voice TTS Route ---

@app.route("/tts", methods=["POST"])
def tts():
    data = request.json or {}
    text = clean_text(data.get("text", ""))
    
    if not text:
        return jsonify({"error": "No text provided"}), 400

    try:
        print("⚡ Sending text to Kaggle F5-TTS Engine...")
        response = requests.post(
            KAGGLE_ENGINE_URL,
            json={"text": text},
            headers={"Bypass-Tunnel-Reminder": "true"},
            timeout=25
        )
        
        if response.status_code == 200:
            res_json = response.json()
            audio_b64 = res_json.get("audio")
            print("✅ Voice successfully generated via Kaggle F5-TTS!")
            return jsonify({"audio": audio_b64, "mimeType": "audio/wav"})
        else:
            print(f"⚠️ Kaggle Engine Error Status: {response.status_code}")
            return jsonify({"error": "Kaggle engine failed"}), 500
            
    except Exception as e:
        print(f"⚠️ Kaggle Engine Connection Exception: {e}")
        return jsonify({"error": "Voice engine unreachable"}), 500

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
