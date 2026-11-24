import json
import re
from difflib import SequenceMatcher


def clean_message_content(content) -> str:
    """Sanitize message content, extracting text if it was stored as raw JSON or list."""
    if not content:
        return ""
    if isinstance(content, str):
        if content.strip().startswith("[") and "signature" in content:
            try:
                parsed = json.loads(content)
                if isinstance(parsed, list):
                    content = "".join(item.get("text", "") for item in parsed if isinstance(item, dict)).strip()
            except Exception:
                pass
        return content.replace("\\n", "\n").replace("\\t", "\t").replace('\\"', '"').strip()
    elif isinstance(content, list):
        content = "".join(item.get("text", "") for item in content if isinstance(item, dict)).strip()
        return content.replace("\\n", "\n").replace("\\t", "\t").replace('\\"', '"').strip()
    return str(content).replace("\\n", "\n").replace("\\t", "\t").replace('\\"', '"').strip()


def normalize_memory(text: str) -> str:
    """Lowercase, drop punctuation and extra spaces so small wording differences don't matter."""
    return " ".join(re.sub(r"[^a-z0-9 ]", " ", text.lower()).split())


def is_duplicate_memory(new_memory: str, profile_text: str) -> bool:
    """True if the new memory is the same as (or almost the same as) a bullet already in the profile."""
    new_memory = normalize_memory(new_memory)
    for line in profile_text.splitlines():
        existing = normalize_memory(line)
        if not existing:
            continue
        # contained in an existing bullet, or ~80% the same wording
        if new_memory in existing or SequenceMatcher(None, new_memory, existing).ratio() >= 0.8:
            return True
    return False
