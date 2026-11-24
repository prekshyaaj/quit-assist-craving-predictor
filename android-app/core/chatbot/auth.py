import os
from fastapi import APIRouter, Header, HTTPException
from supabase import create_client
from config import supabase, logger
from models import AuthRequest

router = APIRouter()


def get_user_id(authorization: str | None = Header(default=None)) -> str:
    """
    The app signs the user in with Supabase Auth and sends their access token as
    "Authorization: Bearer <token>". Supabase checks the token and tells us who it is,
    so the user id can't be faked by the caller.
    """
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Missing sign-in token")

    token = authorization.removeprefix("Bearer ").strip()

    try:
        user_response = supabase.auth.get_user(token)
    except Exception:
        raise HTTPException(status_code=401, detail="Invalid or expired sign-in token")

    if not user_response or not user_response.user:
        raise HTTPException(status_code=401, detail="Invalid or expired sign-in token")

    return user_response.user.id


@router.post("/auth/register")
def register(request: AuthRequest):
    try:
        # Isolated client instance for thread safety
        temp_supabase = create_client(os.getenv("SUPABASE_URL"), os.getenv("SUPABASE_KEY"))
        res = temp_supabase.auth.sign_up({
            "email": request.email.strip(),
            "password": request.password.strip()
        })
        if not res or not res.session:
            if res and res.user:
                try:
                    login_res = temp_supabase.auth.sign_in_with_password({
                        "email": request.email.strip(),
                        "password": request.password.strip()
                    })
                    if login_res and login_res.session:
                        return {
                            "access_token": login_res.session.access_token,
                            "user": {"id": login_res.user.id, "email": login_res.user.email}
                        }
                except Exception:
                    pass
                return {
                    "access_token": "registered",
                    "user": {"id": res.user.id, "email": request.email}
                }
            raise HTTPException(status_code=400, detail="Registration failed")
        return {
            "access_token": res.session.access_token,
            "user": {"id": res.user.id, "email": res.user.email}
        }
    except Exception as e:
        logger.exception("Register error")
        raise HTTPException(status_code=400, detail=str(e))


@router.post("/auth/login")
def login(request: AuthRequest):
    try:
        # Isolated client instance for thread safety
        temp_supabase = create_client(os.getenv("SUPABASE_URL"), os.getenv("SUPABASE_KEY"))
        res = temp_supabase.auth.sign_in_with_password({
            "email": request.email.strip(),
            "password": request.password.strip()
        })
        if not res or not res.session or not res.user:
            raise HTTPException(status_code=401, detail="Invalid email or password")
        return {
            "access_token": res.session.access_token,
            "user": {"id": res.user.id, "email": res.user.email}
        }
    except Exception as e:
        logger.exception("Login error")
        raise HTTPException(status_code=401, detail=str(e))
