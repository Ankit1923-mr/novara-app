"""
Database connection setup.

DATABASE_URL comes from the environment (backend/.env — gitignored,
never committed). Points at Supabase Postgres in production; falls
back to a local SQLite file if unset, so the backend still runs
without a database configured (useful for a quick local check).

Tests override this to an in-memory SQLite database (see
tests/conftest.py) so the test suite never touches the real database
and stays fast/deterministic/network-free.

Engine and session factory are built lazily (not at import time) so
tests can monkeypatch DATABASE_URL and reset the cache before the
first real connection is made — same pattern as llm_client.py's
lazy client construction.
"""

import os
from sqlalchemy import create_engine, inspect, text
from sqlalchemy.orm import sessionmaker, declarative_base
from sqlalchemy.pool import StaticPool
from dotenv import load_dotenv

load_dotenv()

Base = declarative_base()

_engine = None
_SessionLocal = None


def get_engine():
    global _engine
    if _engine is None:
        url = os.environ.get("DATABASE_URL", "sqlite:///./novara_local.db")
        if url.startswith("sqlite"):
            # :memory: needs a StaticPool — otherwise every new connection
            # (one per request) gets its own blank in-memory database and
            # nothing persists across requests, even within one test run.
            is_memory = ":memory:" in url
            _engine = create_engine(
                url,
                connect_args={"check_same_thread": False},
                poolclass=StaticPool if is_memory else None,
            )
        else:
            _engine = create_engine(url)
    return _engine


def get_session_factory():
    global _SessionLocal
    if _SessionLocal is None:
        _SessionLocal = sessionmaker(bind=get_engine(), autoflush=False, autocommit=False)
    return _SessionLocal


def get_db():
    """FastAPI dependency — yields a session, always closes it after the request."""
    db = get_session_factory()()
    try:
        yield db
    finally:
        db.close()


# Columns added to an already-existing table after it was first deployed.
# create_all() below only creates MISSING tables - it never alters a
# table that already exists, so a column added to a model after the
# real production table was created needs an explicit, idempotent
# ALTER TABLE here. Each entry is (table_name, column_name, column_ddl).
# Nullable/no-default only - never a destructive or data-touching change.
_ADDITIVE_COLUMNS = [
    ("scenarios", "chosen_situation", "VARCHAR"),
]


def _add_missing_columns(engine) -> None:
    inspector = inspect(engine)
    for table_name, column_name, column_ddl in _ADDITIVE_COLUMNS:
        if table_name not in inspector.get_table_names():
            continue  # brand-new table - create_all() already created it with every column
        existing_columns = {c["name"] for c in inspector.get_columns(table_name)}
        if column_name in existing_columns:
            continue  # already migrated - safe to call every startup
        with engine.begin() as conn:
            conn.execute(text(f"ALTER TABLE {table_name} ADD COLUMN {column_name} {column_ddl}"))


def init_db():
    """Creates tables if they don't exist, then adds any columns missing
    from tables that already existed before those columns were added to
    the model. Safe to call every startup - a no-op once everything is
    already up to date. Never drops or renames anything, never touches
    existing row data."""
    from app import db_models  # noqa: F401 — ensures models are registered on Base before create_all
    engine = get_engine()
    Base.metadata.create_all(bind=engine)
    _add_missing_columns(engine)


def reset_engine_cache():
    """Test-only: clears the cached engine/session factory so a
    monkeypatched DATABASE_URL takes effect on the next get_engine() call."""
    global _engine, _SessionLocal
    _engine = None
    _SessionLocal = None
