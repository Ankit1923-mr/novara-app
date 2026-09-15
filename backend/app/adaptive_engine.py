"""
NOVARA Adaptive Learning Engine — Review 3 scope (Trip + Casual, Spanish).

Consumes the Knowledge Graph (app.knowledge_graph) to pick the next
scenario for a learner. Standalone module, no FastAPI import, so it's
independently testable; wired into /scenario in main.py.

Selection is rule-based (not ML) for MVP:
    score(situation_tag) = interest_overlap + weak_area_overlap + novelty_bonus

- interest_overlap: profile.interests that appear in the situation's tags
- weak_area_overlap: profile.weak_areas that appear in the situation's tags
  (mostly relevant once weak-area tags like "repair" are used; usually 0
  for skill-type weak areas like "listening" that don't map 1:1 to a
  situation tag — documented in docs/challenges.md)
- novelty_bonus: +1 if this situation was NOT in the learner's recently
  seen list, so the engine doesn't repeat the same scenario back to back

Highest score wins; ties broken by first occurrence in list_situations
(deterministic, easy to unit test).
"""

from typing import Optional
from app.knowledge_graph import get_subgraph, list_situations, Node

# Human-facing scenario metadata per situation tag.
# opening_line is drawn from the first matching graph node at build time.
SCENARIO_META: dict[str, dict] = {
    "cafe": {"title": "Ordering coffee", "setting": "A café in Madrid"},
    "transport": {"title": "Catching the bus", "setting": "A bus stop in Madrid"},
    "hotel": {"title": "Checking into a hotel", "setting": "A hotel reception in Madrid"},
    "restaurant": {"title": "Dinner out", "setting": "A restaurant in Madrid"},
    "airport": {"title": "Arriving at the airport", "setting": "Madrid–Barajas Airport"},
    "directions": {"title": "Asking for directions", "setting": "A street in Madrid"},
    "smalltalk": {"title": "Making small talk", "setting": "A casual conversation with a local"},
    "food": {"title": "Talking about food", "setting": "A tapas bar with a new friend"},
    "hobbies": {"title": "Talking about hobbies", "setting": "A casual hangout"},
    "culture": {"title": "Talking about culture", "setting": "Walking through a Madrid neighborhood"},
    "ordering": {"title": "Ordering something", "setting": "A café in Madrid"},
    "repair": {"title": "Asking someone to repeat", "setting": "Anywhere a conversation stalls"},
    "paying": {"title": "Paying the bill", "setting": "A café or restaurant in Madrid"},
    "checkin": {"title": "Hotel check-in", "setting": "A hotel reception in Madrid"},
    "amenities": {"title": "Asking about amenities", "setting": "A hotel reception in Madrid"},
    "problem": {"title": "Reporting a problem", "setting": "A hotel or airport service desk"},
    "seating": {"title": "Getting a table", "setting": "A restaurant in Madrid"},
    "dietary": {"title": "Explaining dietary needs", "setting": "A restaurant in Madrid"},
    "buying": {"title": "Buying a ticket", "setting": "A train or bus station"},
    "schedules": {"title": "Checking a schedule", "setting": "A train station in Madrid"},
    "plans": {"title": "Making plans", "setting": "A casual conversation with a friend"},
    "movies": {"title": "Talking about movies", "setting": "A casual conversation with a friend"},
}


def _score_situation(tag: str, purpose: str, interests: list[str],
                      weak_areas: list[str], recently_seen: list[str]) -> int:
    nodes = get_subgraph(purpose, situation_tag=tag)
    all_tags: set[str] = set()
    for n in nodes:
        all_tags.update(n["situation_tags"])

    interest_overlap = len(set(interests) & all_tags)
    weak_area_overlap = len(set(weak_areas) & all_tags)
    novelty_bonus = 0 if tag in recently_seen else 1

    return interest_overlap + weak_area_overlap + novelty_bonus


def score_all_situations(purpose: str, interests: Optional[list[str]] = None,
                          weak_areas: Optional[list[str]] = None,
                          recently_seen: Optional[list[str]] = None) -> list[dict]:
    """Score every candidate situation tag for this learner — exposed so the
    frontend can show *why* a scenario was chosen, not just the result."""
    interests = interests or []
    weak_areas = weak_areas or []
    recently_seen = recently_seen or []

    situations = list_situations(purpose)
    breakdown = []
    for tag in situations:
        nodes = get_subgraph(purpose, situation_tag=tag)
        all_tags: set[str] = set()
        for n in nodes:
            all_tags.update(n["situation_tags"])

        interest_overlap = sorted(set(interests) & all_tags)
        weak_area_overlap = sorted(set(weak_areas) & all_tags)
        novel = tag not in recently_seen

        breakdown.append({
            "situation_tag": tag,
            "interest_overlap": interest_overlap,
            "weak_area_overlap": weak_area_overlap,
            "novelty_bonus": 1 if novel else 0,
            "score": len(interest_overlap) + len(weak_area_overlap) + (1 if novel else 0),
        })

    breakdown.sort(key=lambda b: b["score"], reverse=True)
    return breakdown


def choose_situation(purpose: str, interests: Optional[list[str]] = None,
                      weak_areas: Optional[list[str]] = None,
                      recently_seen: Optional[list[str]] = None) -> str:
    """Return the highest-scoring situation tag for this learner."""
    situations = list_situations(purpose)
    if not situations:
        raise ValueError(f"no situations available for purpose={purpose!r}")

    breakdown = score_all_situations(purpose, interests, weak_areas, recently_seen)
    # breakdown is sorted by score desc but ties should break by first
    # occurrence in list_situations, same as the original loop-based version.
    best_score = breakdown[0]["score"]
    top_tags = {b["situation_tag"] for b in breakdown if b["score"] == best_score}
    for tag in situations:
        if tag in top_tags:
            return tag
    return situations[0]


def build_scenario(learner_id: str, purpose: str, interests: Optional[list[str]] = None,
                    weak_areas: Optional[list[str]] = None,
                    recently_seen: Optional[list[str]] = None) -> dict:
    """Full scenario object matching the /scenario contract shape."""
    selection_scores = score_all_situations(purpose, interests, weak_areas, recently_seen)
    tag = choose_situation(purpose, interests, weak_areas, recently_seen)
    nodes: list[Node] = get_subgraph(purpose, situation_tag=tag)
    meta = SCENARIO_META.get(tag, {"title": tag.title(), "setting": "Spain"})
    opening_line = nodes[0]["phrase"] if nodes else "Hola."

    return {
        # purpose is part of the identity, not just the tag - "food" exists
        # under both trip and casual, and without this a scenario built for
        # one purpose could collide with (and be silently overwritten by)
        # one built for the other, since scenario_id is the DB primary key.
        "scenario_id": f"scenario-{purpose}-{tag}",
        "purpose": purpose,
        "title": meta["title"],
        "setting": meta["setting"],
        "situation_tags": sorted({t for n in nodes for t in n["situation_tags"]}),
        "opening_line": opening_line,
        "selection_scores": selection_scores,
        # The single situation this scenario was built around - what
        # recently_seen should track, as opposed to situation_tags above
        # (every tag on every matching phrase, which over-fills the recency
        # window after just one or two scenarios).
        "chosen_situation": tag,
    }
