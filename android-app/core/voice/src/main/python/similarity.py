import json
import os
import re

import numpy as np
import onnxruntime as ort
from tokenizers import Tokenizer

HERE = os.path.dirname(os.path.abspath(__file__))
MODEL_DIR = os.path.join(HERE, "android_model_onnx")
REFERENCE_JSON = os.path.join(HERE, "reference_embeddings.json")

MAX_LENGTH = 128
THRESHOLD = 0.47  # best F1 on test_phrases_1000.csv

session = ort.InferenceSession(os.path.join(MODEL_DIR, "model.onnx"))
tokenizer = Tokenizer.from_file(os.path.join(MODEL_DIR, "tokenizer.json"))
tokenizer.enable_truncation(MAX_LENGTH)
tokenizer.no_padding()

with open(REFERENCE_JSON) as f:
    reference = json.load(f)["phrases"]

reference_phrases = [p["text"] for p in reference]
reference_embeddings = np.array([p["embedding"] for p in reference], dtype=np.float32)
is_craving_ref = np.array([p.get("label", "craving") == "craving" for p in reference])

NEGATION_PATTERNS = [
    r"\b(don't|dont|do not|doesn't|does not|didn't|did not)\s+(want|feel|need|crave|like|think)\b",
    r"\b(not|no)\s+(craving|cravings|urge|urges)\b",
    r"\b(quit|quitting|stopped|stopping|giving up|given up)\s+(smoking|cigarettes|nicotine)\b",
    r"\b(hate|dislike|avoid|refuse|reject)\s+(smoking|cigarettes|nicotine|smoke)\b",
    r"\b(won't|wont|will not|never)\s+(smoke|crave|give in)\b",
    r"\b(no more|not gonna|not going to)\s+(smoke|crave|have a cigarette)\b",
    r"\b(smoke free|smoke-free|nicotine free|nicotine-free)\b",
    r"\b(done with|over|finished with|through with)\s+(smoking|cigarettes|cigs|smokes|nicotine|vaping)\b",
    r"\bno more\s+(cigarettes|cigs|smokes|smoking|nicotine|vaping)\b",
    r"\b(days|weeks|months|years)\s+without\s+(a\s+)?(smoke|smoking|cigarette|cigarettes|cig|cigs|nicotine)\b",
    r"\b(skipped|resisted|resisting|fought off|turned down)\b.*\b(smoke|smoking|cigarette|cig|urge|craving|nicotine)\b",
    r"\b(don't|dont|do not)\s+smoke\b",
]


def is_negated(text):
    text = text.lower()
    return any(re.search(pattern, text) for pattern in NEGATION_PATTERNS)


def embed(text):
    """Mean-pooled, L2-normalized MiniLM embedding (matches reference_embeddings.json)."""
    encoding = tokenizer.encode(text)
    input_ids = np.array([encoding.ids], dtype=np.int64)
    attention_mask = np.array([encoding.attention_mask], dtype=np.int64)
    token_type_ids = np.array([encoding.type_ids], dtype=np.int64)

    hidden = session.run(None, {
        "input_ids": input_ids,
        "attention_mask": attention_mask,
        "token_type_ids": token_type_ids,
    })[0]

    mask = attention_mask[..., None].astype(np.float32)
    embedding = (hidden * mask).sum(axis=1)[0] / max(mask.sum(), 1e-9)
    return embedding / max(np.linalg.norm(embedding), 1e-12)


def score_text(text):
    if not text or is_negated(text):
        return {"text": text, "is_craving": False, "score": 0.0, "match": None}

    scores = reference_embeddings @ embed(text)
    best = np.where(is_craving_ref)[0][scores[is_craving_ref].argmax()]
    best_score = float(scores[best])

    if (~is_craving_ref).any() and scores[~is_craving_ref].max() >= best_score:
        return {"text": text, "is_craving": False, "score": 0.0, "match": None}

    return {
        "text": text,
        "is_craving": best_score >= THRESHOLD,
        "score": round(best_score, 4),
        "match": reference_phrases[best],
    }


def cosine_similarity(text_queue):
    """Score every {"text": ...} entry from whisper; one result per entry."""
    return [score_text(entry["text"]) for entry in text_queue]
