"""
Structural / graph-integrity validator for the NOVARA Knowledge Graph.

Checks EDGES (backend/app/knowledge_graph.py) for:
  - missing endpoints (from_id/to_id not present in GRAPH)
  - duplicate edges (same from_id, to_id, edge_type)
  - self-loops (from_id == to_id), invalid for every current edge type
  - prerequisite cycles - checked ONLY among PREREQUISITE edges;
    RELATED_SITUATION is symmetric by convention (stored as two directed
    edges) and a resulting 2-cycle there is correct, not an error.

Safe to run against an empty EDGES list (today's state) - reports
"no edges to validate" and exits 0. Does not modify any file.

Usage:
    python scripts/validate_knowledge_graph.py
"""

import sys
import os

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from app.knowledge_graph import GRAPH, EDGES, EdgeType


def validate() -> list[str]:
    """Returns a list of human-readable problem descriptions. Empty list
    means the graph is structurally valid."""
    problems: list[str] = []
    node_ids = {n["id"] for n in GRAPH}

    if not EDGES:
        return problems  # nothing to validate yet - not an error

    # Missing endpoints
    for edge in EDGES:
        if edge.from_id not in node_ids:
            problems.append(f"edge references unknown from_id: {edge.from_id!r}")
        if edge.to_id not in node_ids:
            problems.append(f"edge references unknown to_id: {edge.to_id!r}")

    # Duplicate edges (exact from_id, to_id, edge_type tuple)
    seen = set()
    for edge in EDGES:
        key = (edge.from_id, edge.to_id, edge.edge_type)
        if key in seen:
            problems.append(f"duplicate edge: {edge.from_id!r} -> {edge.to_id!r} ({edge.edge_type.value})")
        seen.add(key)

    # Self-loops - invalid for every current edge type
    for edge in EDGES:
        if edge.from_id == edge.to_id:
            problems.append(f"self-loop on {edge.from_id!r} ({edge.edge_type.value})")

    # Prerequisite-only cycle detection (DFS, 3-color / visiting-visited)
    prereq_adjacency: dict[str, list[str]] = {}
    for edge in EDGES:
        if edge.edge_type == EdgeType.PREREQUISITE:
            prereq_adjacency.setdefault(edge.from_id, []).append(edge.to_id)

    visiting: set[str] = set()
    visited: set[str] = set()

    def dfs(node: str, path: list[str]) -> None:
        if node in visited:
            return
        if node in visiting:
            cycle = " -> ".join(path[path.index(node):] + [node])
            problems.append(f"prerequisite cycle detected: {cycle}")
            return
        visiting.add(node)
        for neighbor in prereq_adjacency.get(node, []):
            dfs(neighbor, path + [node])
        visiting.discard(node)
        visited.add(node)

    for start in list(prereq_adjacency.keys()):
        dfs(start, [])

    return problems


def main() -> None:
    problems = validate()
    if not problems:
        print(f"OK - {len(EDGES)} edges validated, 0 problems found.")
        sys.exit(0)
    print(f"FAILED - {len(problems)} problem(s) found:")
    for p in problems:
        print(f"  - {p}")
    sys.exit(1)


if __name__ == "__main__":
    main()
