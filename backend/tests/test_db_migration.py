"""Regression test for db.py's additive-column migration (_add_missing_columns).

create_all() only creates tables that don't exist yet - it never alters
a table that already exists, so a column added to a model after the
real production table was first deployed (chosen_situation on
ScenarioModel) needs this explicit, idempotent migration. This test
simulates exactly that: a pre-existing table built from the OLD schema,
with a real row in it, then confirms init_db() adds the column without
touching that row."""

import os
import tempfile
from sqlalchemy import text

from app.db import reset_engine_cache, get_engine, init_db


def test_additive_column_migration_preserves_existing_data():
    fd, db_path = tempfile.mkstemp(suffix=".db")
    os.close(fd)
    try:
        original_url = os.environ.get("DATABASE_URL")
        try:
            os.environ["DATABASE_URL"] = f"sqlite:///{db_path}"
            reset_engine_cache()
            engine = get_engine()

            # Build the OLD pre-migration schema by hand (no chosen_situation
            # column) and insert a real row - this is what a production
            # table deployed before this column existed actually looks like.
            with engine.begin() as conn:
                conn.execute(text(
                    "CREATE TABLE scenarios ("
                    "scenario_id VARCHAR PRIMARY KEY, purpose VARCHAR NOT NULL, "
                    "title VARCHAR NOT NULL, setting VARCHAR NOT NULL, "
                    "situation_tags JSON, opening_line VARCHAR NOT NULL)"
                ))
                conn.execute(text(
                    "INSERT INTO scenarios (scenario_id, purpose, title, setting, situation_tags, opening_line) "
                    "VALUES ('scenario-trip-cafe', 'trip', 'Ordering coffee', 'A cafe', '[\"cafe\"]', 'Hola')"
                ))

            init_db()

            with engine.connect() as conn:
                row = dict(conn.execute(text("SELECT * FROM scenarios")).mappings().first())
            assert row["scenario_id"] == "scenario-trip-cafe"
            assert row["title"] == "Ordering coffee"
            assert "chosen_situation" in row
            assert row["chosen_situation"] is None  # pre-existing row - column added as NULL, no data invented

            # Idempotency: calling init_db() again (e.g. next backend restart)
            # must not error on a column that's already there.
            init_db()
        finally:
            engine.dispose()  # release the file handle before cleanup removes it (Windows locks open files)
            if original_url is None:
                os.environ.pop("DATABASE_URL", None)
            else:
                os.environ["DATABASE_URL"] = original_url
            reset_engine_cache()
            os.environ["DATABASE_URL"] = "sqlite:///:memory:"
            reset_engine_cache()
            init_db()
    finally:
        os.remove(db_path)
