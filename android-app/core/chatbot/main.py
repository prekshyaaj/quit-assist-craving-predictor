from fastapi import FastAPI
from auth import router as auth_router
from router_chat import router as chat_router
from router_questionnaire import router as questionnaire_router
from router_home import router as home_router

app = FastAPI(title="Smoking Cessation Chatbot")

app.include_router(auth_router)
app.include_router(chat_router)
app.include_router(questionnaire_router)
app.include_router(home_router)


@app.get("/health")
def health():
    return {"status": "ok"}
