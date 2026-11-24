import os
import subprocess
import time

import requests

HERE = os.path.dirname(os.path.abspath(__file__))

WHISPER_BINARY = os.environ.get("WHISPER_BINARY", "whisper-server")
WHISPER_MODEL = os.environ.get("WHISPER_MODEL", os.path.join(HERE, "models", "ggml-tiny.en.bin"))
WHISPER_URL = "http://127.0.0.1:8178"


def start_whisper(binary=WHISPER_BINARY, model=WHISPER_MODEL):
    """Launch whisper-server once; it keeps the model loaded and reads uploads from memory."""
    process = subprocess.Popen([
        binary,
        "-m", model,
        "-l", "en",
        "--host", "127.0.0.1",
        "--port", "8178",
    ])
    wait_until_ready(process)
    return process


def wait_until_ready(process, timeout=60):
    deadline = time.time() + timeout
    while time.time() < deadline:
        if process.poll() is not None:
            raise RuntimeError("whisper-server exited during startup")
        try:
            if requests.get(f"{WHISPER_URL}/health", timeout=1).ok:
                return
        except requests.ConnectionError:
            pass
        time.sleep(0.5)
    process.kill()
    raise RuntimeError("whisper-server did not become ready")


def transcribe(wav_bytes):
    response = requests.post(
        f"{WHISPER_URL}/inference",
        files={"file": ("segment.wav", wav_bytes, "audio/wav")},
        data={"response_format": "json", "temperature": "0.0"},
        timeout=60,
    )
    response.raise_for_status()
    return response.json()["text"].strip()


def create_text_queue(audio_queue):
    """Transcribe each audio segment independently: one {"text": ...} entry per segment, in order."""
    text_queue = []
    while not audio_queue.empty():
        text_queue.append({"text": transcribe(audio_queue.get())})
    return text_queue
