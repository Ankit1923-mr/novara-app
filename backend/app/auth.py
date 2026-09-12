"""
API key authentication for NOVARA endpoints.

MVP scope: a single shared static key (NOVARA_API_KEY, backend/.env),
sent by the Android app as the X-API-Key header. Not per-user auth —
that's a bigger feature than this project needs; this exists purely to
stop the live public endpoint from being callable by anyone who finds
the URL, which would otherwise let a stranger burn through the
free-tier LLM quota or fill the database with junk learners.

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
    expected_key = os.environ.get("NOVARA_API_KEY")

    if not expected_key:
        # Misconfiguration, not a client error — fail loudly so it's caught
        # in deployment rather than silently accepting every request.
        raise HTTPException(status_code=500, detail="Server misconfigured: NOVARA_API_KEY not set")

    if not provided_key or provided_key != expected_key:
        raise HTTPException(status_code=401, detail="Missing or invalid API key")
