"""
API key authentication for NOVARA endpoints.

Two static keys are accepted (backend/.env):
- NOVARA_API_KEY: the real key, used by the Android app. Never put this
  in anything public.
- NOVARA_API_KEY_DEMO: a separate key embedded in the public live-demo
  webpage (for reviewers to interact with the real backend directly).
  Public by design and cheap to rotate — revoking it never affects the
  Android app, since they're independent values checked the same way.

Not per-user auth — that's a bigger feature than this project needs;
this exists purely to stop the live public endpoint from being callable
by anyone who finds the URL, which would otherwise let a stranger burn
through the free-tier LLM quota or fill the database with junk learners.

The health check endpoint (GET /) is intentionally NOT behind this —
Render's health monitor and quick manual checks need it open.
"""

import os
from fastapi import Security, HTTPException
from fastapi.security import APIKeyHeader
from dotenv import load_dotenv

load_dotenv()

_api_key_header = APIKeyHeader(name="X-API-Key", auto_error=False)


def verify_api_key(provided_key: str = Security(_api_key_header)) -> None:
    valid_keys = {
        k for k in (os.environ.get("NOVARA_API_KEY"), os.environ.get("NOVARA_API_KEY_DEMO"))
        if k
    }

    if not valid_keys:
        # Misconfiguration, not a client error — fail loudly so it's caught
        # in deployment rather than silently accepting every request.
        raise HTTPException(status_code=500, detail="Server misconfigured: no API key configured")

    if not provided_key or provided_key not in valid_keys:
        raise HTTPException(status_code=401, detail="Missing or invalid API key")
