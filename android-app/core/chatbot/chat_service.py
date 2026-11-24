import uuid
from fastapi import HTTPException
from langchain_core.messages import HumanMessage, SystemMessage
from config import supabase, logger, llm, RECENT_MESSAGES, SUMMARY_BATCH
from utils import clean_message_content, is_duplicate_memory


def load_own_session(session_id: str, user_id: str, columns: str) -> dict:
    """Returns the session row if it exists AND belongs to this user (so one user can't open another user's chat), else 404."""
    try:
        uuid.UUID(session_id)
    except ValueError:
        raise HTTPException(status_code=404, detail="Session not found for this user")

    try:
        session_result = (
            supabase
            .table("sessions")
            .select(columns)
            .eq("id", session_id)
            .eq("user_id", user_id)
            .maybe_single()
            .execute()
        )
    except Exception:
        logger.exception("Error loading session")
        raise HTTPException(status_code=500, detail="Could not load the session")

    if not (session_result and session_result.data):
        raise HTTPException(status_code=404, detail="Session not found for this user")

    return session_result.data


def update_global_profile(user_id: str, memory_update_needed: bool, memory_information: str):
    if not memory_update_needed:
        return

    memory_information = (memory_information or "").strip().lstrip("-").strip()

    if not memory_information or memory_information.lower().strip(".") == "none":
        logger.info("Gemini requested a memory update but provided no information.")
        return

    try:
        profile_result = (
            supabase
            .table("global_profile")
            .select("profile_text")
            .eq("user_id", user_id)
            .maybe_single()
            .execute()
        )
    except Exception as e:
        logger.warning("Global profile not updated (could not load it): %s", e)
        return

    if not (profile_result and profile_result.data):
        try:
            supabase.table("global_profile").insert({
                "user_id": user_id,
                "profile_text": "- " + memory_information
            }).execute()
        except Exception as e:
            logger.warning("Error creating global profile: %s", e)
        return

    current_global_profile = profile_result.data.get("profile_text") or ""

    if is_duplicate_memory(memory_information, current_global_profile):
        logger.info("Memory already present in the global profile, not added.")
        return

    if current_global_profile.strip():
        updated_global_profile = current_global_profile + "\n- " + memory_information
    else:
        updated_global_profile = "- " + memory_information

    try:
        supabase.table("global_profile").update({
            "profile_text": updated_global_profile
        }).eq("user_id", user_id).execute()
    except Exception as e:
        logger.warning("Error updating global profile: %s", e)


def update_session_summary(session_id: str, session_summary: str | None, unsummarized_rows: list):
    old_rows = unsummarized_rows[:-RECENT_MESSAGES]

    if len(old_rows) < SUMMARY_BATCH:
        return

    old_text = "\n".join(
        f'{row["role"]}: {clean_message_content(row.get("content", ""))}'
        for row in old_rows
    )

    summary_prompt = [
        SystemMessage(
            content=(
                "You are maintaining a concise summary of a smoking "
                "cessation conversation. Update the existing summary "
                "using the new older messages. Preserve important "
                "information about cravings, triggers, smoking patterns, "
                "progress, setbacks, preferences, and coping strategies. "
                "Do not invent information."
            )
        ),
        HumanMessage(
            content=(
                f"Existing session summary:\n"
                f"{session_summary or 'No existing summary.'}\n\n"
                f"New messages to incorporate:\n"
                f"{old_text}"
            )
        )
    ]

    try:
        summary_response = llm.invoke(summary_prompt)
        new_session_summary = clean_message_content(summary_response.content)
    except Exception as e:
        logger.warning("Error generating session summary (will retry next time): %s", e)
        return

    if not new_session_summary:
        return

    try:
        supabase.table("sessions").update({
            "session_summary": new_session_summary
        }).eq("id", session_id).execute()

        old_ids = [row["id"] for row in old_rows]

        supabase.table("messages").update({
            "folded_into_summary": True
        }).in_("id", old_ids).execute()
    except Exception as e:
        logger.warning("Error saving session summary: %s", e)
