from typing import Literal
from pydantic import BaseModel, Field


class AuthRequest(BaseModel):
    email: str
    password: str


class ChatRequest(BaseModel):
    message: str
    session_id: str | None = None


class ChatReply(BaseModel):
    session_id: str
    reply: str


class SessionInfo(BaseModel):
    id: str
    status: str
    started_at: str
    ended_at: str | None = None


class MessageInfo(BaseModel):
    id: str
    role: str           # "user" or "assistant"
    content: str
    created_at: str


class ChatbotResponse(BaseModel):
    assistant_response: str
    memory_update_needed: bool
    memory_reason: str
    memory_information: str


# Codes follow the NHIS training data (dataset_prepro.csv).
EmploymentCode = Literal[10, 11, 12, 13, 14, 101, 102, 103, 104, 105, 106, 108]


class QuestionnaireData(BaseModel):
    AGEP_A: int = Field(ge=18, le=85)        # 85 means "85 or older"
    SEX_A: Literal[1, 2]                     # 1 = male, 2 = female
    CIGNOW_A: float = Field(ge=1, le=95)     # cigarettes per day; 95 means "95 or more"
    EMP_STAT: EmploymentCode


class QuestionnaireResponse(BaseModel):
    id: str
    AGEP_A: int | None = None
    SEX_A: int | None = None
    CIGNOW_A: float | None = None
    EMP_STAT: int | None = None


class DayCount(BaseModel):
    date: str            # local date, YYYY-MM-DD
    cigarettes: int
    tracked: bool        # False for future days and days before tracking started


class ProgressStats(BaseModel):
    money_saved: float
    cigarettes_avoided: int
    life_regained_minutes: int
    minutes_since_last_cigarette: int | None = None


class HomeSummary(BaseModel):
    today: str
    week: list[DayCount]     # Sunday to Saturday of the current week
    streak: int              # smoke-free days in a row, up to today
    sober_coins: int         # finished smoke-free days since tracking started
    progress: ProgressStats

