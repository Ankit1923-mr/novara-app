"""
NOVARA Knowledge Graph — Spanish, Trip + Casual purposes (Review 3 scope).

Nodes are situational language units: a phrase tied to meaning, register,
region, cultural context and the situation(s) it belongs to. This module
is deliberately standalone (no FastAPI import) so it can be built and
tested independently of the rest of the backend, then wired into
/scenario by the Adaptive Learning Engine.

Graph shape: GRAPH is still a flat list of nodes (purpose/situation_tag/
region filtering is unaffected by anything below — get_subgraph and
list_situations keep their exact existing signatures and behavior).
"Edges" between these nodes are implicit — two nodes sharing a
situation_tag are connected by that situation; querying by tag
traverses that edge.

EDGES is new: an explicit, directed, typed edge list — the one honest
graph structure this module has (everything above is still a tagged
catalogue, not a graph, per the architecture review this was built
against). It starts empty: no prerequisite or related_situation edges
have been authored yet for the existing 34 nodes, deliberately deferred
to a separate, later-approved task. The traversal functions and
validator below are correct and safe against an empty EDGES list - they
exist now so the structure is provable before any content is authored
against it, not because content already uses it.
"""

from collections import defaultdict
from dataclasses import dataclass
from enum import Enum
from typing import Optional

Node = dict


class EdgeType(str, Enum):
    # Directed, asymmetric: from_id must be learned before to_id. Must be
    # acyclic - see the validator in backend/scripts/validate_knowledge_graph.py.
    PREREQUISITE = "prerequisite"
    # Symmetric real-world fact (two situations commonly co-occur).
    # Stored as two directed edges (A->B and B->A) so every edge type
    # shares one representation and one query path - see
    # related_situations_of() below. A resulting 2-cycle here is correct,
    # not an error; the acyclic rule applies only to PREREQUISITE.
    RELATED_SITUATION = "related_situation"


@dataclass(frozen=True)
class Edge:
    from_id: str
    to_id: str
    edge_type: EdgeType


# Explicit, authored edges - not derived from situation_tags. Empty for
# now; see the module docstring above for why.
EDGES: list[Edge] = []

GRAPH: list[Node] = [
    # --- Trip: café / ordering ---
    {"id": "cafe_01", "phrase": "Quiero un café, por favor.", "meaning": "I'd like a coffee, please.",
     "register": "neutral", "region": "spain", "culture_note": "Standard polite ordering phrase.",
     "situation_tags": ["cafe", "ordering", "food"], "purpose": "trip"},
    {"id": "cafe_02", "phrase": "¿Para aquí o para llevar?", "meaning": "For here or to go?",
     "register": "neutral", "region": "spain", "culture_note": "Common server question in any café.",
     "situation_tags": ["cafe", "ordering"], "purpose": "trip"},
    {"id": "cafe_03", "phrase": "¿Me pone la cuenta, por favor?", "meaning": "Could you bring the bill, please?",
     "register": "formal", "region": "spain", "culture_note": "You ask for the bill; it isn't brought unprompted.",
     "situation_tags": ["cafe", "paying"], "purpose": "trip"},
    {"id": "cafe_04", "phrase": "¿Puede repetir, por favor?", "meaning": "Could you repeat that, please?",
     "register": "formal", "region": "spain", "culture_note": "Standard comprehension-repair request.",
     "situation_tags": ["cafe", "repair"], "purpose": "trip"},

    # --- Trip: transport ---
    {"id": "transport_01", "phrase": "¿Dónde está la parada de autobús?", "meaning": "Where is the bus stop?",
     "register": "neutral", "region": "spain", "culture_note": "Standard direction-asking pattern.",
     "situation_tags": ["transport", "directions"], "purpose": "trip"},
    {"id": "transport_02", "phrase": "Un billete a Madrid, por favor.", "meaning": "One ticket to Madrid, please.",
     "register": "neutral", "region": "spain", "culture_note": "Ticket counters use por favor consistently.",
     "situation_tags": ["transport", "buying"], "purpose": "trip"},
    {"id": "transport_03", "phrase": "¿A qué hora sale el próximo tren?", "meaning": "What time does the next train leave?",
     "register": "neutral", "region": "spain", "culture_note": "24-hour clock is standard on schedules.",
     "situation_tags": ["transport", "schedules"], "purpose": "trip"},
    {"id": "transport_04", "phrase": "¿Este autobús va al centro?", "meaning": "Does this bus go downtown?",
     "register": "neutral", "region": "spain", "culture_note": "Confirming route before boarding is normal.",
     "situation_tags": ["transport", "directions"], "purpose": "trip"},

    # --- Trip: hotel ---
    {"id": "hotel_01", "phrase": "Tengo una reserva a nombre de...", "meaning": "I have a reservation under the name...",
     "register": "formal", "region": "spain", "culture_note": "Check-in opener at most hotels.",
     "situation_tags": ["hotel", "checkin"], "purpose": "trip"},
    {"id": "hotel_02", "phrase": "¿A qué hora es el check-out?", "meaning": "What time is check-out?",
     "register": "neutral", "region": "spain", "culture_note": "check-out used as loanword even in Spanish.",
     "situation_tags": ["hotel", "checkin"], "purpose": "trip"},
    {"id": "hotel_03", "phrase": "¿El desayuno está incluido?", "meaning": "Is breakfast included?",
     "register": "neutral", "region": "spain", "culture_note": "Common clarifying question at budget hotels.",
     "situation_tags": ["hotel", "amenities"], "purpose": "trip"},
    {"id": "hotel_04", "phrase": "No funciona el wifi de la habitación.", "meaning": "The room wifi isn't working.",
     "register": "neutral", "region": "spain", "culture_note": "Common complaint phrasing at reception.",
     "situation_tags": ["hotel", "problem"], "purpose": "trip"},

    # --- Trip: restaurant ---
    {"id": "restaurant_01", "phrase": "¿Tiene una mesa para dos?", "meaning": "Do you have a table for two?",
     "register": "formal", "region": "spain", "culture_note": "Walk-ins are normal for smaller restaurants.",
     "situation_tags": ["restaurant", "seating"], "purpose": "trip"},
    {"id": "restaurant_02", "phrase": "¿Qué me recomienda?", "meaning": "What do you recommend?",
     "register": "formal", "region": "spain", "culture_note": "Waiters expect and welcome this question.",
     "situation_tags": ["restaurant", "ordering"], "purpose": "trip"},
    {"id": "restaurant_03", "phrase": "Soy alérgico/a a los frutos secos.", "meaning": "I'm allergic to nuts.",
     "register": "neutral", "region": "spain", "culture_note": "Allergy disclosure phrase, important for safety.",
     "situation_tags": ["restaurant", "dietary"], "purpose": "trip"},
    {"id": "restaurant_04", "phrase": "La cuenta, cuando pueda.", "meaning": "The bill, whenever you can.",
     "register": "neutral", "region": "spain", "culture_note": "No rush culturally to bring or ask for the bill.",
     "situation_tags": ["restaurant", "paying"], "purpose": "trip"},

    # --- Trip: directions / small talk overlap ---
    {"id": "directions_01", "phrase": "¿Cómo llego a la Plaza Mayor?", "meaning": "How do I get to Plaza Mayor?",
     "register": "neutral", "region": "spain", "culture_note": "Plaza Mayor is a common Madrid landmark reference.",
     "situation_tags": ["directions"], "purpose": "trip"},
    {"id": "directions_02", "phrase": "¿Está lejos de aquí?", "meaning": "Is it far from here?",
     "register": "neutral", "region": "spain", "culture_note": "Common follow-up after asking directions.",
     "situation_tags": ["directions"], "purpose": "trip"},

    # --- Casual: small talk ---
    {"id": "smalltalk_01", "phrase": "¿Qué tal el finde?", "meaning": "How was the weekend?",
     "register": "informal", "region": "spain", "culture_note": "finde is colloquial short for fin de semana.",
     "situation_tags": ["smalltalk"], "purpose": "casual"},
    {"id": "smalltalk_02", "phrase": "¿A qué te dedicas?", "meaning": "What do you do (for work)?",
     "register": "neutral", "region": "spain", "culture_note": "Common icebreaker among new acquaintances.",
     "situation_tags": ["smalltalk"], "purpose": "casual"},
    {"id": "smalltalk_03", "phrase": "¡Qué guay!", "meaning": "That's cool!",
     "register": "informal", "region": "spain", "culture_note": "guay is Spain-specific slang for 'cool'.",
     "situation_tags": ["smalltalk"], "purpose": "casual"},
    {"id": "smalltalk_04", "phrase": "Nos vemos luego.", "meaning": "See you later.",
     "register": "informal", "region": "spain", "culture_note": "Casual parting phrase among friends.",
     "situation_tags": ["smalltalk"], "purpose": "casual"},

    # --- Casual: food ---
    {"id": "food_01", "phrase": "¿Has probado el jamón ibérico?", "meaning": "Have you tried Iberian ham?",
     "register": "informal", "region": "spain", "culture_note": "A cultural touchstone, common casual food topic.",
     "situation_tags": ["food", "smalltalk"], "purpose": "casual"},
    {"id": "food_02", "phrase": "Me encanta la comida picante.", "meaning": "I love spicy food.",
     "register": "informal", "region": "spain", "culture_note": "Spanish cuisine is generally mild, so this stands out.",
     "situation_tags": ["food", "smalltalk"], "purpose": "casual"},
    {"id": "food_03", "phrase": "¿Vamos a tapear esta noche?", "meaning": "Shall we go for tapas tonight?",
     "register": "informal", "region": "spain", "culture_note": "tapear is a colloquial verb for 'go out for tapas'.",
     "situation_tags": ["food", "plans"], "purpose": "casual"},

    # --- Casual: movies / hobbies ---
    {"id": "hobbies_01", "phrase": "¿Qué peli viste el finde?", "meaning": "What movie did you watch this weekend?",
     "register": "informal", "region": "spain", "culture_note": "peli is colloquial short for película.",
     "situation_tags": ["movies", "smalltalk"], "purpose": "casual"},
    {"id": "hobbies_02", "phrase": "Me flipa el fútbol.", "meaning": "I'm obsessed with football.",
     "register": "informal", "region": "spain", "culture_note": "flipar is strong Spain slang for loving something.",
     "situation_tags": ["hobbies", "smalltalk"], "purpose": "casual"},
    {"id": "hobbies_03", "phrase": "¿Tocas algún instrumento?", "meaning": "Do you play any instrument?",
     "register": "neutral", "region": "spain", "culture_note": "Common casual hobby-topic question.",
     "situation_tags": ["hobbies", "smalltalk"], "purpose": "casual"},

    # --- Casual: culture / history light-touch ---
    {"id": "culture_01", "phrase": "Este barrio tiene mucha historia.", "meaning": "This neighborhood has a lot of history.",
     "register": "neutral", "region": "spain", "culture_note": "Common observational remark while sightseeing casually.",
     "situation_tags": ["culture", "smalltalk"], "purpose": "casual"},
    {"id": "culture_02", "phrase": "¿Cuál es tu plato típico favorito?", "meaning": "What's your favorite traditional dish?",
     "register": "neutral", "region": "spain", "culture_note": "Opens into regional-food conversation.",
     "situation_tags": ["culture", "food", "smalltalk"], "purpose": "casual"},

    # --- Repair-adjacent phrases usable in both purposes ---
    {"id": "repair_01", "phrase": "No entiendo, ¿puede hablar más despacio?", "meaning": "I don't understand, could you speak slower?",
     "register": "formal", "region": "spain", "culture_note": "Polite comprehension-repair, works in any formal setting.",
     "situation_tags": ["repair"], "purpose": "trip"},
    {"id": "repair_02", "phrase": "Perdona, ¿qué significa eso?", "meaning": "Sorry, what does that mean?",
     "register": "informal", "region": "spain", "culture_note": "Casual comprehension-repair among peers.",
     "situation_tags": ["repair", "smalltalk"], "purpose": "casual"},

    # --- Extra trip coverage: airport ---
    {"id": "airport_01", "phrase": "¿Dónde está la recogida de equipaje?", "meaning": "Where is baggage claim?",
     "register": "neutral", "region": "spain", "culture_note": "Common airport-arrival question.",
     "situation_tags": ["airport", "directions"], "purpose": "trip"},
    {"id": "airport_02", "phrase": "He perdido mi maleta.", "meaning": "I've lost my suitcase.",
     "register": "neutral", "region": "spain", "culture_note": "Key phrase for baggage-service counters.",
     "situation_tags": ["airport", "problem"], "purpose": "trip"},
]


def get_subgraph(purpose: str, situation_tag: Optional[str] = None, region: Optional[str] = None) -> list[Node]:
    """Filter the graph by purpose, and optionally by situation tag and region."""
    result = [n for n in GRAPH if n["purpose"] == purpose]
    if situation_tag:
        result = [n for n in result if situation_tag in n["situation_tags"]]
    if region:
        result = [n for n in result if n["region"] == region]
    return result


def list_situations(purpose: str) -> list[str]:
    """Unique situation tags available for a given purpose — used by the
    Adaptive Learning Engine to pick which scenario to offer next."""
    tags: set[str] = set()
    for n in GRAPH:
        if n["purpose"] == purpose:
            tags.update(n["situation_tags"])
    return sorted(tags)


# Built once at import time from GRAPH/EDGES, same pattern as GRAPH itself
# being a module-level constant. _NODE_INDEX gives get_node() O(1) lookup
# instead of the previous linear scan; _OUT_ADJACENCY/_IN_ADJACENCY back
# the traversal functions below. Rebuilding these at import time (rather
# than lazily) keeps them trivially correct for a static, code-defined
# graph - there is no mutation path at runtime to go stale against.
_NODE_INDEX: dict[str, Node] = {n["id"]: n for n in GRAPH}
_OUT_ADJACENCY: dict[str, list[Edge]] = defaultdict(list)
_IN_ADJACENCY: dict[str, list[Edge]] = defaultdict(list)
for _edge in EDGES:
    _OUT_ADJACENCY[_edge.from_id].append(_edge)
    _IN_ADJACENCY[_edge.to_id].append(_edge)


def get_node(node_id: str) -> Optional[Node]:
    return _NODE_INDEX.get(node_id)


def prerequisites_of(node_id: str) -> list[str]:
    """Direct (immediate) prerequisites of node_id - one hop. Empty list
    if none (including for an unknown node_id - never raises); this is
    the safe default every one of today's 34 nodes has, since no
    prerequisite edges have been authored yet."""
    return [e.from_id for e in _IN_ADJACENCY.get(node_id, []) if e.edge_type == EdgeType.PREREQUISITE]


def all_prerequisites_of(node_id: str) -> list[str]:
    """Full transitive closure of prerequisites, via an explicit-stack
    iterative DFS over PREREQUISITE edges only (not recursive - at
    1,000+ nodes a sufficiently deep prerequisite chain could exceed
    Python's default recursion limit with a recursive implementation;
    an explicit stack has no such bound). Returns a dependency-ordered
    list: every prerequisite appears before anything that depends on it.
    Empty list if none.

    Raises ValueError if a cycle is reachable from node_id. This should
    be unreachable in practice - the validator in
    backend/scripts/validate_knowledge_graph.py rejects cycles before any
    content ships - so this is a defensive check, not the primary
    enforcement mechanism."""
    ordered: list[str] = []
    seen: set[str] = set()
    visiting: set[str] = set()
    # Each stack frame is (node, its direct prerequisites, next index into
    # that list to process) - the explicit "next index" is what replaces
    # the call stack a recursive version would otherwise use.
    stack: list[tuple[str, list[str], int]] = []

    for start in prerequisites_of(node_id):
        if start in seen:
            continue
        if start in visiting:
            raise ValueError(f"prerequisite cycle detected reachable from {node_id!r} at {start!r}")
        visiting.add(start)
        stack.append((start, prerequisites_of(start), 0))

        while stack:
            current, deps, idx = stack[-1]
            if idx < len(deps):
                nxt = deps[idx]
                stack[-1] = (current, deps, idx + 1)
                if nxt in seen:
                    continue
                if nxt in visiting:
                    raise ValueError(f"prerequisite cycle detected reachable from {node_id!r} at {nxt!r}")
                visiting.add(nxt)
                stack.append((nxt, prerequisites_of(nxt), 0))
            else:
                stack.pop()
                visiting.discard(current)
                seen.add(current)
                ordered.append(current)

    return ordered


def unlocks(node_id: str) -> list[str]:
    """Direct nodes that list node_id as an immediate prerequisite - the
    inverse of prerequisites_of. Empty list if none."""
    return [e.to_id for e in _OUT_ADJACENCY.get(node_id, []) if e.edge_type == EdgeType.PREREQUISITE]


def related_situations_of(node_id: str) -> list[str]:
    """Nodes related to node_id via a RELATED_SITUATION edge. Symmetric by
    authoring convention (see EdgeType.RELATED_SITUATION) - both
    directions are stored explicitly, so this single lookup is correct
    from either side of the relationship. Empty list if none."""
    return [e.to_id for e in _OUT_ADJACENCY.get(node_id, []) if e.edge_type == EdgeType.RELATED_SITUATION]
