import os
import re
import requests
from flask import Flask, jsonify, render_template, session

app = Flask(__name__, template_folder='.')
app.secret_key = "saara_secret_key_123"

# Environment Variable se read karega (GitHub par koi error nahi aayega)
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", "")
OPENROUTER_API_KEY = os.getenv("OPENROUTER_API_KEY", "")
NVIDIA_API_KEY = os.getenv("NVIDIA_API_KEY", "")
BYTEZ_API_KEY = os.getenv("BYTEZ_API_KEY", "")


SYSTEM_PROMPT = (
    "You are Saara, a warm, polite, cheerful, and friendly female AI assistant for Devraj sir. "
    "Always provide 100% accurate factual answers in natural Hinglish. "
    "Talk naturally and respectfully to Devraj. "
    "Do NOT use emojis, asterisks (*), hashtags, or special formatting."
)

def clean_text(text):
    if not text:
        return ""
    text = re.sub(r'[*#]', '', text)
    return text.strip()

# 1. Gemini Provider (With Multi-Model Auto-Fallback)
# Master Gemini Provider Function (All 3.x, 2.5, Gemma 4 Models Merged)
def call_gemini(history):
    gemini_models = [
        # --- 3.x Series Models ---
        "gemini-3.6-flash",
        "gemini-3.5-flash",
        "gemini-3.5-flash-lite",
        "gemini-3.1-flash-lite",
        "gemini-3-flash-preview",
        
        # --- 2.5 Series Models ---
        "gemini-2.5-flash-lite",
        "gemini-2.5-flash",
        "gemini-2.5-pro",
        "gemini-2.5-flash-lite-preview-09-2025",
        
        # --- Gemma 4 Models (High 14.4K RPD) ---
        "gemma-4-31b-it",
        "gemma-4-26b-it",
        "gemma-4-31b",
        "gemma-4-26b",
        
        # --- Stable Backup Models ---
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

    # Master Model Fallback Loop
    for model in gemini_models:
        url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
        try:
            res = requests.post(url, headers=headers, json=payload, timeout=8)
            if res.status_code == 200:
                print(f"✅ Success with Model: [{model}]")
                return res.json()["candidates"][0]["content"]["parts"][0]["text"]
            else:
                print(f"⚠️ [{model}] Status {res.status_code}. Trying next model...")
        except Exception:
            continue

    raise Exception("All Gemini and Gemma models failed or rate limited")

# 2. OpenRouter Provider
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

# 3. NVIDIA Provider
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

# 4. Bytez Provider
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

    return jsonify({"reply": "Sabhi API services busy hain Devraj sir, thodi der baad try karein."})

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000)
