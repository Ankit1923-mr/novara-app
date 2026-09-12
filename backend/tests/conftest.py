"""
Forces the whole test suite onto an isolated in-memory SQLite database
instead of the real Supabase Postgres instance — tests must never touch
production data or require network access to run.
"""

import os
import pytest


@pytest.fixture(autouse=True, scope="session")
def use_in_memory_test_database():
    os.environ["DATABASE_URL"] = "sqlite:///:memory:"
    from app.db import reset_engine_cache, init_db
    reset_engine_cache()
    init_db()
    yield
