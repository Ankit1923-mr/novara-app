"""
Independent tests for the Knowledge Graph module — no FastAPI dependency.
Baseline (Review 3): >=30 nodes, both purposes non-empty, filters work,
covers >=5 situation categories per purpose.

The traversal tests below exercise the real graph structure (EdgeType,
Edge, prerequisites_of/all_prerequisites_of/unlocks/related_situations_of)
added alongside the existing flat node list. EDGES is empty in the
shipped module (no prerequisite/related_situation content has been
authored yet, deliberately deferred) — each test injects temporary edges
directly into the module's adjacency dicts via monkeypatch, so it
exercises genuine traversal/cycle-detection logic without permanently
adding any edge to EDGES itself.
"""

import pytest

import app.knowledge_graph as kg
from app.knowledge_graph import (
    GRAPH, get_subgraph, list_situations, get_node,
    Edge, EdgeType, prerequisites_of, all_prerequisites_of, unlocks, related_situations_of,
)


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


def _inject_edges(monkeypatch, edges: list[Edge]) -> None:
    """Rebuilds the module's adjacency dicts from `edges` for the
    duration of one test — fully reverted by monkeypatch's automatic
    teardown, never touches EDGES. Node ids in `edges` don't need to
    exist in GRAPH (these functions only follow adjacency, they don't
    validate node existence — that's the validator script's job), so
    tests use either real existing ids (e.g. cafe_01) or synthetic ones
    (e.g. a long chain), whichever makes the test clearest."""
    out_adj: dict[str, list[Edge]] = {}
    in_adj: dict[str, list[Edge]] = {}
    for e in edges:
        out_adj.setdefault(e.from_id, []).append(e)
        in_adj.setdefault(e.to_id, []).append(e)
    monkeypatch.setattr(kg, "_OUT_ADJACENCY", out_adj)
    monkeypatch.setattr(kg, "_IN_ADJACENCY", in_adj)


def test_prerequisites_of_returns_direct_predecessors(monkeypatch):
    # cafe_02 requires cafe_01 first.
    _inject_edges(monkeypatch, [Edge("cafe_01", "cafe_02", EdgeType.PREREQUISITE)])
    assert prerequisites_of("cafe_02") == ["cafe_01"]
    assert prerequisites_of("cafe_01") == []  # no edges point into cafe_01


def test_transitive_prerequisite_chain_order(monkeypatch):
    # cafe_03 requires cafe_02 requires cafe_01 — full chain, dependency order.
    _inject_edges(monkeypatch, [
        Edge("cafe_01", "cafe_02", EdgeType.PREREQUISITE),
        Edge("cafe_02", "cafe_03", EdgeType.PREREQUISITE),
    ])
    assert all_prerequisites_of("cafe_03") == ["cafe_01", "cafe_02"]


def test_deep_prerequisite_chain_does_not_hit_recursion_limit(monkeypatch):
    # NOVARA targets 1,000+ nodes - a prerequisite chain that long (or
    # longer) must not blow Python's recursion limit. Builds a synthetic
    # chain well past sys.getrecursionlimit()'s default (1000) and
    # confirms all_prerequisites_of still returns the correct, fully
    # ordered result instead of raising RecursionError.
    chain_length = 3000
    ids = [f"chain_{i}" for i in range(chain_length)]
    edges = [Edge(ids[i], ids[i + 1], EdgeType.PREREQUISITE) for i in range(chain_length - 1)]
    _inject_edges(monkeypatch, edges)

    result = all_prerequisites_of(ids[-1])

    assert result == ids[:-1]  # every earlier link, in dependency order
    assert len(result) == chain_length - 1


def test_cycle_is_detected_and_rejected(monkeypatch):
    # cafe_01 -> cafe_02 -> cafe_01: a genuine prerequisite cycle.
    _inject_edges(monkeypatch, [
        Edge("cafe_01", "cafe_02", EdgeType.PREREQUISITE),
        Edge("cafe_02", "cafe_01", EdgeType.PREREQUISITE),
    ])
    with pytest.raises(ValueError):
        all_prerequisites_of("cafe_01")


def test_adjacency_lookup_for_node_with_no_edges_returns_empty_not_error():
    # EDGES is empty by default — every existing node has no edges yet.
    assert prerequisites_of("cafe_01") == []
    assert unlocks("cafe_01") == []
    assert related_situations_of("cafe_01") == []
    # Unknown node id: same safe-empty behavior, never raises.
    assert prerequisites_of("does_not_exist") == []


def test_related_situation_edges_are_bidirectionally_queryable(monkeypatch):
    # Authored as two directed edges representing one symmetric fact.
    _inject_edges(monkeypatch, [
        Edge("cafe_01", "cafe_02", EdgeType.RELATED_SITUATION),
        Edge("cafe_02", "cafe_01", EdgeType.RELATED_SITUATION),
    ])
    assert related_situations_of("cafe_01") == ["cafe_02"]
    assert related_situations_of("cafe_02") == ["cafe_01"]


def test_get_node_lookup_is_correct_after_migration_to_dict_index():
    # Same observable behavior as before the linear-scan -> _NODE_INDEX
    # change — a regression check, not new behavior.
    node = get_node("cafe_01")
    assert node is not None and node["id"] == "cafe_01"
    assert get_node("does_not_exist") is None


def test_get_node_reads_from_node_index_not_a_graph_scan(monkeypatch):
    # Proves get_node() actually consults _NODE_INDEX rather than scanning
    # GRAPH, not just that results happen to be correct: a sentinel node
    # is injected into _NODE_INDEX only (NOT into GRAPH). A linear scan
    # over GRAPH would never find it and would return None; the real
    # implementation must return it, since it reads _NODE_INDEX directly.
    sentinel = {"id": "__test_sentinel__", "phrase": "x", "meaning": "x"}
    patched_index = dict(kg._NODE_INDEX)
    patched_index["__test_sentinel__"] = sentinel
    monkeypatch.setattr(kg, "_NODE_INDEX", patched_index)

    assert all(n["id"] != "__test_sentinel__" for n in GRAPH)  # confirms it's absent from GRAPH
    assert get_node("__test_sentinel__") is sentinel
