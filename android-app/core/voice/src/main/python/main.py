import os
from contextlib import asynccontextmanager

from fastapi import Body, FastAPI, Header, HTTPException

from similarity import cosine_similarity
from vad import create_audio_queue
from whisper import create_text_queue, start_whisper

API_KEY = os.environ.get("API_KEY")


@asynccontextmanager
async def lifespan(app):
    whisper_process = start_whisper()
    yield
    whisper_process.terminate()


app = FastAPI(lifespan=lifespan)


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/analyze")
def analyze(
    pcm: bytes = Body(..., media_type="application/octet-stream"),
    x_api_key: str | None = Header(default=None),
):
    """Body: raw 16 kHz mono 16-bit PCM. Returns one result per detected speech segment."""
    if API_KEY and x_api_key != API_KEY:
        raise HTTPException(status_code=401, detail="Invalid API key")

    audio_queue = create_audio_queue(pcm)
    text_queue = create_text_queue(audio_queue)
    return cosine_similarity(text_queue)
