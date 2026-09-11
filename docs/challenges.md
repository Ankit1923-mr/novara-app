# Challenges & Technical Refinement Log

Log every real problem you hit and what you did about it — this is what rubric line 5 ("problem-solving and technical refinement") is graded on. Add an entry as it happens, not retroactively.

## Format
```
### [Date] Short title
**Problem**: what broke or underperformed
**Cause**: root cause if known
**Fix**: what you changed
**Result**: metric before → after, if applicable
```

## Log

### [2026-09-11] Knowledge Graph representation: flat list vs. graph library

**Decision (not a bug)**: Implemented the Language–Culture–Life Knowledge Graph as a flat list of tagged node dicts, queried by purpose/situation_tag/region, instead of a graph library (NetworkX/Neo4j).

**Why it came up**: The module is named "Knowledge Graph" in the architecture, which creates pressure to use an actual graph library/data structure to look technically substantial.

**Reasoning**: At MVP scale (35 nodes, Trip+Casual only), filtering is O(n) regardless of representation — a graph library adds a dependency and query-language overhead with no functional benefit yet. Edges are still present, just implicit: two nodes sharing a `situation_tags` value are connected by that situation, and `get_subgraph`/`list_situations` traverse exactly that relationship.

**Result**: Kept the flat-list implementation. Interface (`get_subgraph`, `list_situations`, `get_node`) is graph-library-agnostic, so swapping to NetworkX or Neo4j later — if scenario complexity grows past MVP — is a drop-in change, not a rewrite.
