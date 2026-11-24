from datetime import datetime, timezone
from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException
from langchain_core.messages import AIMessage, HumanMessage, SystemMessage

from config import logger, structured_llm, supabase, system_prompt
from models import ChatReply, ChatRequest, MessageInfo, SessionInfo
from auth import get_user_id
from chat_service import (
    load_own_session,
    update_global_profile,
    update_session_summary,
)
from utils import clean_message_content

router = APIRouter()


@router.post("/chat", response_model=ChatReply)
def chat(
    request: ChatRequest,
    background_tasks: BackgroundTasks,
    user_id: str = Depends(get_user_id),
):
    current_user_message = request.message.strip()

    if not current_user_message:
        raise HTTPException(status_code=400, detail="Message is empty")

    user_message_time = datetime.now(timezone.utc)

    if request.session_id:
        session = load_own_session(request.session_id, user_id, "session_summary, status")
        if session.get("status") == "ended":
            raise HTTPException(status_code=409, detail="This chat has ended, start a new one")
        session_id = request.session_id
        session_summary = clean_message_content(session.get("session_summary")) or None
    else:
        try:
            supabase.table("users").upsert(
                {"id": user_id},
                on_conflict="id",
                ignore_duplicates=True
            ).execute()

            new_session = supabase.table("sessions").insert({
                "user_id": user_id
            }).execute()
        except Exception:
            logger.exception("Error creating session")
            raise HTTPException(status_code=500, detail="Could not start a new session")

        session_id = new_session.data[0]["id"]
        session_summary = None

    try:
        profile_result = (
            supabase
            .table("global_profile")
            .select("profile_text")
            .eq("user_id", user_id)
            .maybe_single()
            .execute()
        )
        profile_text = (
            profile_result.data["profile_text"]
            if profile_result and profile_result.data
            else None
        )
    except Exception as e:
        logger.warning("Error loading global profile: %s", e)
        profile_text = None

    try:
        unsummarized_result = (
            supabase
            .table("messages")
            .select("id, role, content, created_at")
            .eq("session_id", session_id)
            .or_("folded_into_summary.is.null,folded_into_summary.is.false")
            .order("created_at")
            .execute()
        )
        unsummarized_rows = unsummarized_result.data
    except Exception as e:
        logger.warning("Error loading session messages: %s", e)
        unsummarized_rows = []

    messages = []
    for row in unsummarized_rows:
        clean_text = clean_message_content(row.get("content", ""))
        if row["role"] == "user":
            messages.append(HumanMessage(content=clean_text))
        elif row["role"] == "assistant":
            messages.append(AIMessage(content=clean_text))

    global_context = profile_text or "No global profile available."
    session_context = session_summary or "No session summary available."
    recent_context = "\n".join(
        f"{msg.type}: {msg.content}"
        for msg in messages
    )

    prompt = [
        SystemMessage(content=system_prompt),
        HumanMessage(
            content=(
                f"""
===== EXISTING GLOBAL PROFILE =====

{global_context}

===== CURRENT SESSION SUMMARY =====

{session_context}

===== RECENT CONVERSATION =====

{recent_context}

===== CURRENT USER MESSAGE =====

{current_user_message}

Respond naturally to the user's message and perform the
memory analysis using the structured output format.
"""
            )
        )
    ]

    try:
        response = structured_llm.invoke(prompt)
    except Exception:
        logger.exception("Error calling Gemini")
        raise HTTPException(status_code=502, detail="The chatbot is unavailable right now, please try again")

    if response is None:
        raise HTTPException(status_code=502, detail="The chatbot could not answer, please try again")

    assistant_response = response.assistant_response

    saved_rows = []
    try:
        saved_result = supabase.table("messages").insert([
            {
                "session_id": session_id,
                "user_id": user_id,
                "role": "user",
                "content": current_user_message,
                "created_at": user_message_time.isoformat()
            },
            {
                "session_id": session_id,
                "user_id": user_id,
                "role": "assistant",
                "content": assistant_response,
                "created_at": datetime.now(timezone.utc).isoformat()
            }
        ]).execute()
        saved_rows = saved_result.data
    except Exception:
        logger.exception("Error saving current interaction")

    background_tasks.add_task(
        update_global_profile,
        user_id,
        response.memory_update_needed,
        response.memory_information
    )

    background_tasks.add_task(
        update_session_summary,
        session_id,
        session_summary,
        unsummarized_rows + saved_rows
    )

    return ChatReply(session_id=session_id, reply=assistant_response)


@router.get("/sessions", response_model=list[SessionInfo])
def list_sessions(user_id: str = Depends(get_user_id)):
    try:
        sessions_result = (
            supabase
            .table("sessions")
            .select("id, status, started_at, ended_at")
            .eq("user_id", user_id)
            .order("started_at", desc=True)
            .execute()
        )
    except Exception:
        logger.exception("Error listing sessions")
        raise HTTPException(status_code=500, detail="Could not load your chats")

    return sessions_result.data


@router.get("/sessions/{session_id}/messages", response_model=list[MessageInfo])
def get_session_messages(session_id: str, user_id: str = Depends(get_user_id)):
    load_own_session(session_id, user_id, "id")
    try:
        messages_result = (
            supabase
            .table("messages")
            .select("id, role, content, created_at")
            .eq("session_id", session_id)
            .order("created_at")
            .execute()
        )
    except Exception:
        logger.exception("Error loading chat history")
        raise HTTPException(status_code=500, detail="Could not load this chat")

    return [
        {**row, "content": clean_message_content(row.get("content", ""))}
        for row in messages_result.data
    ]


@router.post("/sessions/{session_id}/end", response_model=SessionInfo)
def end_session(session_id: str, user_id: str = Depends(get_user_id)):
    session = load_own_session(session_id, user_id, "id, status, started_at, ended_at")
    if session.get("status") == "ended":
        return session

    try:
        ended_result = supabase.table("sessions").update({
            "status": "ended",
            "ended_at": datetime.now(timezone.utc).isoformat()
        }).eq("id", session_id).eq("user_id", user_id).execute()
    except Exception:
        logger.exception("Error ending session")
        raise HTTPException(status_code=500, detail="Could not end this chat")

    row = ended_result.data[0]
    return {key: row[key] for key in ("id", "status", "started_at", "ended_at")}


@router.delete("/sessions/{session_id}", status_code=204)
def delete_session(session_id: str, user_id: str = Depends(get_user_id)):
    load_own_session(session_id, user_id, "id")
    try:
        supabase.table("messages").delete().eq("session_id", session_id).eq("user_id", user_id).execute()
        supabase.table("sessions").delete().eq("id", session_id).eq("user_id", user_id).execute()
    except Exception:
        logger.exception("Error deleting session")
        raise HTTPException(status_code=500, detail="Could not delete this chat")
