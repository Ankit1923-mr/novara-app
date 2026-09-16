"""
NOVARA Lesson & Quiz content — Spanish, three seed topics.

Scope, stated honestly: this is real, hand-authored content (not
placeholder text), but only 3 topics deep and rule-based grading, not
a generative curriculum engine. It demonstrates the intended
teach -> quiz -> track-mistakes loop end to end; scaling to a full
curriculum (more topics, harder branching logic) is future work.

Each vocabulary item carries a cultural_note - per the product
requirement that cultural context is taught alongside the phrase
itself, not as a separate unit.

Each quiz question is tied to one vocabulary phrase (link_phrase) so
grading can report exactly which phrases the learner got wrong,
feeding LearnerModel.mistake_words.
"""

TOPICS = [
    {
        "topic_id": "greetings",
        "title": "Greetings and Courtesy",
        "order_index": 1,
        "vocabulary": [
            {"phrase": "Hola", "meaning": "Hello",
             "cultural_note": "Used any time of day, in both formal and informal settings — unlike English, it doesn't shift by time of day."},
            {"phrase": "Buenos dias", "meaning": "Good morning",
             "cultural_note": "Used until roughly midday; Spanish greetings track the actual position of the sun more strictly than English does."},
            {"phrase": "Por favor", "meaning": "Please",
             "cultural_note": "Expected in almost every request, even casual ones — omitting it among strangers can read as brusque."},
            {"phrase": "Gracias", "meaning": "Thank you",
             "cultural_note": "Often paired with a small head nod; repeating it twice ('gracias, gracias') signals genuine warmth, not impatience."},
            {"phrase": "De nada", "meaning": "You're welcome",
             "cultural_note": "Literally 'it's nothing' — downplaying the favor is the polite move, the opposite of English 'you're welcome'."},
        ],
        "quiz": [
            {"question": "What does 'Hola' mean, and when is it used?",
             "options": ["Good night, evenings only", "Hello, any time of day", "Goodbye, formal contexts", "Please, when asking for something"],
             "correct_index": 1, "link_phrase": "Hola"},
            {"question": "'Buenos dias' is typically used:",
             "options": ["Only after sunset", "Any time of day", "Roughly until midday", "Only in writing"],
             "correct_index": 2, "link_phrase": "Buenos dias"},
            {"question": "Choose the correct translation of 'Por favor':",
             "options": ["Thank you", "You're welcome", "Excuse me", "Please"],
             "correct_index": 3, "link_phrase": "Por favor"},
            {"question": "'De nada' most literally translates to:",
             "options": ["It's nothing", "No problem at all", "Don't mention it", "My pleasure"],
             "correct_index": 0, "link_phrase": "De nada"},
        ],
    },
    {
        "topic_id": "cafe_basics",
        "title": "Ordering at a Cafe",
        "order_index": 2,
        "vocabulary": [
            {"phrase": "Quiero un cafe, por favor", "meaning": "I'd like a coffee, please",
             "cultural_note": "Standard polite ordering phrase — 'quiero' (I want) is not considered blunt here, unlike a literal English translation might suggest."},
            {"phrase": "Para aqui o para llevar", "meaning": "For here or to go",
             "cultural_note": "A server will almost always ask this — sit-down cafe culture is strong in Spain, so 'for here' is the unmarked default."},
            {"phrase": "La cuenta, por favor", "meaning": "The bill, please",
             "cultural_note": "The bill is not brought automatically, even after you've clearly finished — you must ask for it, or you may sit for a while."},
            {"phrase": "Puede repetir, por favor", "meaning": "Could you repeat that, please",
             "cultural_note": "A normal, unembarrassed way to ask for repetition — Spanish speakers use it often even with each other in noisy cafes."},
        ],
        "quiz": [
            {"question": "You want to order a coffee politely. What do you say?",
             "options": ["Cafe, ahora", "Quiero un cafe, por favor", "Dame cafe", "Necesito cafe"],
             "correct_index": 1, "link_phrase": "Quiero un cafe, por favor"},
            {"question": "A server asks 'Para aqui o para llevar?'. They are asking:",
             "options": ["What size you want", "If you want sugar", "Dine in or takeaway", "How you'll pay"],
             "correct_index": 2, "link_phrase": "Para aqui o para llevar"},
            {"question": "In Spain, the bill at a cafe is usually:",
             "options": ["Brought automatically once you finish", "Never given, you pay at the counter", "Given only if you ask for it", "Included in the coffee price"],
             "correct_index": 2, "link_phrase": "La cuenta, por favor"},
        ],
    },
    {
        "topic_id": "numbers_time",
        "title": "Numbers and Time",
        "order_index": 3,
        "vocabulary": [
            {"phrase": "Uno, dos, tres", "meaning": "One, two, three",
             "cultural_note": "Spain uses the 24-hour clock in almost all official and spoken contexts, unlike casual American English."},
            {"phrase": "Que hora es", "meaning": "What time is it",
             "cultural_note": "A very common everyday question — Spanish social plans are often looser on exact time than in Anglophone cultures, so this gets asked a lot."},
            {"phrase": "Son las tres", "meaning": "It's three o'clock",
             "cultural_note": "Hours use 'son las' (plural) for every hour except one o'clock, which uses 'es la' — a common early mistake."},
            {"phrase": "Media hora", "meaning": "Half an hour",
             "cultural_note": "Spanish daily schedules (lunch, dinner) run noticeably later than in much of Northern Europe or the US — worth knowing when making plans."},
        ],
        "quiz": [
            {"question": "'Que hora es' is asking:",
             "options": ["What day is it", "What time is it", "How old are you", "Where is the clock"],
             "correct_index": 1, "link_phrase": "Que hora es"},
            {"question": "To say 'It's three o'clock', you say:",
             "options": ["Es las tres", "Son las tres", "Estoy tres", "Tengo tres"],
             "correct_index": 1, "link_phrase": "Son las tres"},
            {"question": "'Media hora' means:",
             "options": ["Midnight", "Half an hour", "Mid-morning", "An hour and a half"],
             "correct_index": 1, "link_phrase": "Media hora"},
        ],
    },
]


def list_topics() -> list[dict]:
    return [{"topic_id": t["topic_id"], "title": t["title"], "order_index": t["order_index"]} for t in TOPICS]


def get_topic(topic_id: str) -> dict | None:
    for t in TOPICS:
        if t["topic_id"] == topic_id:
            return t
    return None


def grade_quiz(topic_id: str, answers: list[int]) -> dict:
    """Grades `answers` (one index per quiz question, in order) against
    the topic's quiz. Returns score, correct_count, total, and the list
    of vocabulary phrases tied to questions the learner missed."""
    topic = get_topic(topic_id)
    if topic is None:
        raise ValueError(f"unknown topic_id: {topic_id!r}")

    quiz = topic["quiz"]
    correct_count = 0
    missed_phrases = []
    # Per-question correctness, in order — lets the caller run the same
    # per-turn pace adjustment used in /conversation across quiz questions,
    # instead of quiz mistakes only affecting mistake_words.
    per_question_correct: list[bool] = []

    for i, question in enumerate(quiz):
        given = answers[i] if i < len(answers) else None
        is_correct = given == question["correct_index"]
        per_question_correct.append(is_correct)
        if is_correct:
            correct_count += 1
        else:
            missed_phrases.append(question["link_phrase"])

    total = len(quiz)
    score = round(correct_count / total, 4) if total else 0.0

    return {
        "topic_id": topic_id,
        "score": score,
        "correct_count": correct_count,
        "total": total,
        "missed_phrases": missed_phrases,
        "per_question_correct": per_question_correct,
    }
