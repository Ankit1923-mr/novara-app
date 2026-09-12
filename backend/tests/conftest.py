"""
Forces the whole test suite onto an isolated in-memory SQLite database
instead of the real Supabase Postgres instance — tests must never touch
production data or require network access to run.

Also pins NOVARA_API_KEY to a fixed test value rather than whatever
real key is in backend/.env — tests should never depend on (or leak
into assertions) the actual production secret.
"""

import os
import pytest

TEST_API_KEY = "test-api-key-for-pytest-only"


@pytest.fixture(autouse=True, scope="session")
def use_in_memory_test_database():
    os.environ["DATABASE_URL"] = "sqlite:///:memory:"
    os.environ["NOVARA_API_KEY"] = TEST_API_KEY
    from app.db import reset_engine_cache, init_db
    reset_engine_cache()
    init_db()
    yield


@pytest.fixture(autouse=True)
def reset_rate_limiter():
    """slowapi's TestClient requests all report the same fake IP
    ("testclient"), so every test would otherwise share one rate-limit
    bucket and later tests could get 429'd by an earlier test's calls
    to /conversation. Reset before each test for isolation."""
    from app.main import limiter
    limiter.reset()
    yield
