"""
Independent tests for the Knowledge Graph module — no FastAPI dependency.
Baseline (Review 3): >=30 nodes, both purposes non-empty, filters work,
covers >=5 situation categories per purpose.
"""

from app.knowledge_graph import GRAPH, get_subgraph, list_situations, get_node


def test_minimum_node_count():
    assert len(GRAPH) >= 30


def test_both_purposes_present():
    trip = get_subgraph("trip")
    casual = get_subgraph("casual")
    assert len(trip) > 0
    assert len(casual) > 0


def test_trip_purpose_filter_only_returns_trip_nodes():
    trip = get_subgraph("trip")
    assert all(n["purpose"] == "trip" for n in trip)


def test_casual_purpose_filter_only_returns_casual_nodes():
    casual = get_subgraph("casual")
    assert all(n["purpose"] == "casual" for n in casual)


def test_situation_tag_filter():
    cafe_nodes = get_subgraph("trip", situation_tag="cafe")
    assert len(cafe_nodes) > 0
    assert all("cafe" in n["situation_tags"] for n in cafe_nodes)


def test_situation_tag_filter_with_no_match_returns_empty():
    result = get_subgraph("trip", situation_tag="nonexistent_tag")
    assert result == []


def test_region_filter():
    spain_nodes = get_subgraph("trip", region="spain")
    assert len(spain_nodes) > 0
    assert all(n["region"] == "spain" for n in spain_nodes)


def test_list_situations_covers_at_least_five_categories_per_purpose():
    trip_situations = list_situations("trip")
    casual_situations = list_situations("casual")
    assert len(trip_situations) >= 5
    assert len(casual_situations) >= 5


def test_get_node_by_id_returns_correct_node():
    node = get_node("cafe_01")
    assert node is not None
    assert node["id"] == "cafe_01"
    assert "phrase" in node and "meaning" in node


def test_get_node_by_unknown_id_returns_none():
    assert get_node("does_not_exist") is None


def test_every_node_has_required_fields():
    required = {"id", "phrase", "meaning", "register", "region", "culture_note", "situation_tags", "purpose"}
    for n in GRAPH:
        assert required.issubset(n.keys()), f"node {n.get('id')} missing fields"
        assert n["purpose"] in ("trip", "casual")
        assert isinstance(n["situation_tags"], list) and len(n["situation_tags"]) > 0


def test_no_duplicate_node_ids():
    ids = [n["id"] for n in GRAPH]
    assert len(ids) == len(set(ids))
