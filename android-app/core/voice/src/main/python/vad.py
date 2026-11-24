import io
import queue
import wave
from collections import deque

import webrtcvad

SAMPLE_RATE = 16000
CHANNELS = 1
BYTES_PER_SAMPLE = 2
FRAME_MS = 30
FRAME_BYTES = SAMPLE_RATE * FRAME_MS // 1000 * BYTES_PER_SAMPLE

PRE_BUFFER_FRAMES = 600 // FRAME_MS   # audio kept from just before speech starts
TRIGGER_FRAMES = 8                    # consecutive speech frames needed to start a segment
HANGOVER_FRAMES = 2500 // FRAME_MS    # silence frames needed to end a segment

vad = webrtcvad.Vad(3)


def pcm_to_wav(pcm):
    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as wf:
        wf.setnchannels(CHANNELS)
        wf.setsampwidth(BYTES_PER_SAMPLE)
        wf.setframerate(SAMPLE_RATE)
        wf.writeframes(pcm)
    return buffer.getvalue()


def create_audio_queue(pcm):
    """Split raw 16 kHz mono 16-bit PCM into speech segments, as a queue of in-memory WAV bytes."""
    audio_queue = queue.Queue()
    pre_buffer = deque(maxlen=PRE_BUFFER_FRAMES)
    segment = []
    recording = False
    speech_run = 0
    silence_run = 0

    for start in range(0, len(pcm) - FRAME_BYTES + 1, FRAME_BYTES):
        frame = pcm[start:start + FRAME_BYTES]
        is_speech = vad.is_speech(frame, SAMPLE_RATE)

        if not recording:
            pre_buffer.append(frame)
            speech_run = speech_run + 1 if is_speech else 0
            if speech_run >= TRIGGER_FRAMES:
                recording = True
                segment = list(pre_buffer)
                silence_run = 0
        else:
            segment.append(frame)
            silence_run = 0 if is_speech else silence_run + 1
            if silence_run >= HANGOVER_FRAMES:
                audio_queue.put(pcm_to_wav(b"".join(segment)))
                recording = False
                pre_buffer.clear()
                speech_run = 0

    if recording:
        audio_queue.put(pcm_to_wav(b"".join(segment)))

    return audio_queue
