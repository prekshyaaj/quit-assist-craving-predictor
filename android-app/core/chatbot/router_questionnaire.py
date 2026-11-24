from fastapi import APIRouter, Depends, HTTPException
from config import logger, supabase
from models import QuestionnaireData, QuestionnaireResponse
from auth import get_user_id

router = APIRouter()

COLUMNS = "id, AGEP_A, SEX_A, CIGNOW_A, EMP_STAT"


@router.get("/questionnaire", response_model=QuestionnaireResponse)
def get_questionnaire(user_id: str = Depends(get_user_id)):
    try:
        res = supabase.table("users").select(COLUMNS).eq("id", user_id).maybe_single().execute()
    except Exception:
        logger.exception("Error loading questionnaire")
        raise HTTPException(status_code=500, detail="Could not load questionnaire data")

    # No row yet means nothing has been answered.
    return res.data if res else QuestionnaireResponse(id=user_id)


@router.post("/questionnaire", response_model=QuestionnaireResponse)
def save_questionnaire(data: QuestionnaireData, user_id: str = Depends(get_user_id)):
    fields = data.model_dump()
    try:
        res = supabase.table("users").upsert({"id": user_id, **fields}, on_conflict="id").execute()
    except Exception:
        logger.exception("Error saving questionnaire")
        raise HTTPException(status_code=500, detail="Could not save questionnaire data")

    return res.data[0] if res.data else QuestionnaireResponse(id=user_id, **fields)
