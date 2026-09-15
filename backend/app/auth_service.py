"""
Web-app account authentication: password hashing and JWT session tokens.

Scope, stated honestly: this issues real accounts with bcrypt-hashed
passwords and signed JWT sessions. It does NOT send verification or
password-reset emails - that needs a transactional email provider
(Resend, SendGrid, or real SMTP credentials) that isn't configured.
UserModel.email_verified exists as a field and is always False; there
is deliberately no /auth/forgot-password endpoint yet rather than a
route that claims to email a reset link and silently does nothing.

This is a separate concern from app/auth.py (verify_api_key), which
gates every endpoint against the shared NOVARA_API_KEY / NOVARA_API_KEY_DEMO
and is unaffected by any of this - a web-app request still needs both
a valid API key AND a valid JWT.
"""

import os
import bcrypt
import jwt
from datetime import datetime, timedelta, timezone
from typing import Optional
from dotenv import load_dotenv

load_dotenv()

JWT_ALGORITHM = "HS256"
JWT_EXPIRY_HOURS = 24 * 14  # 2 weeks - long enough not to force re-login during a demo


def _get_jwt_secret() -> str:
    secret = os.environ.get("JWT_SECRET")
    if not secret:
        raise RuntimeError("JWT_SECRET not set in environment")
    return secret


def hash_password(plain_password: str) -> str:
    return bcrypt.hashpw(plain_password.encode("utf-8"), bcrypt.gensalt()).decode("utf-8")


def verify_password(plain_password: str, password_hash: str) -> bool:
    try:
        return bcrypt.checkpw(plain_password.encode("utf-8"), password_hash.encode("utf-8"))
    except ValueError:
        # malformed stored hash - fail closed, never raise into a 500 for this
        return False


def create_session_token(email: str) -> str:
    payload = {
        "sub": email,
        "exp": datetime.now(timezone.utc) + timedelta(hours=JWT_EXPIRY_HOURS),
        "iat": datetime.now(timezone.utc),
    }
    return jwt.encode(payload, _get_jwt_secret(), algorithm=JWT_ALGORITHM)


def decode_session_token(token: str) -> Optional[str]:
    """Returns the email (sub claim) if the token is valid, else None."""
    try:
        payload = jwt.decode(token, _get_jwt_secret(), algorithms=[JWT_ALGORITHM])
        return payload.get("sub")
    except jwt.PyJWTError:
        return None
