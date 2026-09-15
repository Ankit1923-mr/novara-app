"""Independent tests for lesson_content.py — no FastAPI dependency."""

from app.lesson_content import TOPICS, list_topics, get_topic, grade_quiz


def test_at_least_three_topics_exist():
    assert len(TOPICS) >= 3


def test_every_topic_has_vocabulary_with_cultural_notes():
    for topic in TOPICS:
        assert topic["vocabulary"], f"{topic['topic_id']} has no vocabulary"
        for item in topic["vocabulary"]:
            assert item["cultural_note"].strip(), f"{item['phrase']} missing a cultural note"


def test_every_quiz_question_links_to_a_real_vocabulary_phrase():
    for topic in TOPICS:
        phrases = {v["phrase"] for v in topic["vocabulary"]}
        for q in topic["quiz"]:
            assert q["link_phrase"] in phrases, f"{q['link_phrase']} not in {topic['topic_id']}'s vocabulary"
            assert 0 <= q["correct_index"] < len(q["options"])


def test_list_topics_returns_summaries_without_quiz_content():
    summaries = list_topics()
    assert len(summaries) == len(TOPICS)
    assert "quiz" not in summaries[0]
    assert "vocabulary" not in summaries[0]


def test_get_topic_returns_full_detail():
    topic = get_topic("greetings")
    assert topic is not None
    assert topic["title"] == "Greetings and Courtesy"
    assert len(topic["vocabulary"]) > 0


def test_get_unknown_topic_returns_none():
    assert get_topic("does_not_exist") is None


def test_grade_quiz_perfect_score():
    topic = get_topic("greetings")
    correct_answers = [q["correct_index"] for q in topic["quiz"]]
    result = grade_quiz("greetings", correct_answers)
    assert result["score"] == 1.0
    assert result["correct_count"] == result["total"]
    assert result["missed_phrases"] == []


def test_grade_quiz_all_wrong():
    topic = get_topic("greetings")
    wrong_answers = [(q["correct_index"] + 1) % len(q["options"]) for q in topic["quiz"]]
    result = grade_quiz("greetings", wrong_answers)
    assert result["score"] == 0.0
    assert result["correct_count"] == 0
    assert len(result["missed_phrases"]) == result["total"]


def test_grade_quiz_missed_phrases_match_wrong_questions():
    topic = get_topic("cafe_basics")
    answers = [q["correct_index"] for q in topic["quiz"]]
    answers[0] = (answers[0] + 1) % len(topic["quiz"][0]["options"])  # force question 0 wrong
    result = grade_quiz("cafe_basics", answers)
    assert topic["quiz"][0]["link_phrase"] in result["missed_phrases"]
    assert result["correct_count"] == result["total"] - 1


def test_grade_quiz_unknown_topic_raises():
    import pytest
    with pytest.raises(ValueError):
        grade_quiz("does_not_exist", [0])


def test_grade_quiz_handles_missing_answers_gracefully():
    topic = get_topic("numbers_time")
    result = grade_quiz("numbers_time", [])  # no answers submitted at all
    assert result["correct_count"] == 0
    assert result["total"] == len(topic["quiz"])
