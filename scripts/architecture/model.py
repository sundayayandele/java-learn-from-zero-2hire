"""Architecture model and renderers: one model per diagram, emitted as Mermaid and draw.io (diagrams.net) XML.

A diagram is a list of columns (laid out left to right). Each column is a Group: a labelled container
(subgraph in Mermaid, swimlane in draw.io) or an unlabelled stack of nodes. Edges join node ids.
"""
from __future__ import annotations

import html
from dataclasses import dataclass, field

# Node kinds and how each renderer draws them.
KINDS = {
    #          mermaid open/close,  draw.io style,                                                        Mermaid class
    "actor":   ('(["', '"])', "rounded=1;arcSize=50;whiteSpace=wrap;html=1;fillColor=#dae8fc;strokeColor=#6c8ebf;", "actor"),
    "service": ('["', '"]', "rounded=1;whiteSpace=wrap;html=1;fillColor=#d5e8d4;strokeColor=#82b366;", "svc"),
    "comp":    ('["', '"]', "rounded=1;whiteSpace=wrap;html=1;fillColor=#ffffff;strokeColor=#666666;", "comp"),
    "db":      ('[("', '")]', "shape=cylinder3;whiteSpace=wrap;html=1;boundedLbl=1;backgroundOutline=1;size=12;fillColor=#fff2cc;strokeColor=#d6b656;", "db"),
    "store":   ('[("', '")]', "shape=cylinder3;whiteSpace=wrap;html=1;boundedLbl=1;backgroundOutline=1;size=12;fillColor=#f5f5f5;strokeColor=#666666;", "store"),
    "ext":     ('[/"', '"/]', "shape=parallelogram;perimeter=parallelogramPerimeter;whiteSpace=wrap;html=1;fixedSize=1;fillColor=#f8cecc;strokeColor=#b85450;dashed=1;", "ext"),
    "infra":   ('["', '"]', "rounded=0;whiteSpace=wrap;html=1;fillColor=#e1d5e7;strokeColor=#9673a6;", "infra"),
    "job":     ('[["', '"]]', "shape=process;whiteSpace=wrap;html=1;backgroundOutline=1;fillColor=#ffe6cc;strokeColor=#d79b00;", "job"),
}

MERMAID_CLASSES = """    classDef actor fill:#dae8fc,stroke:#6c8ebf,color:#000
    classDef svc fill:#d5e8d4,stroke:#82b366,color:#000
    classDef comp fill:#ffffff,stroke:#666666,color:#000
    classDef db fill:#fff2cc,stroke:#d6b656,color:#000
    classDef store fill:#f5f5f5,stroke:#666666,color:#000
    classDef ext fill:#f8cecc,stroke:#b85450,color:#000,stroke-dasharray: 5 3
    classDef infra fill:#e1d5e7,stroke:#9673a6,color:#000
    classDef job fill:#ffe6cc,stroke:#d79b00,color:#000"""


@dataclass
class Node:
    id: str
    label: str
    kind: str = "comp"


@dataclass
class Group:
    label: str | None
    nodes: list[Node]
    id: str = ""


@dataclass
class Edge:
    src: str
    dst: str
    label: str = ""
    dashed: bool = False


@dataclass
class Diagram:
    kind: str                      # infra | technical | cloud | logical | solution
    title: str
    explanation: str               # markdown
    columns: list[Group]
    edges: list[Edge]
    direction: str = "LR"

    def nodes(self) -> dict[str, Node]:
        return {n.id: n for g in self.columns for n in g.nodes}

    def validate(self) -> list[str]:
        ids = self.nodes()
        problems = [f"{self.kind}: edge {e.src}->{e.dst} references unknown node"
                    for e in self.edges if e.src not in ids or e.dst not in ids]
        problems += [f"{self.kind}: unknown kind {n.kind}" for n in ids.values() if n.kind not in KINDS]
        return problems


@dataclass
class Decision:
    title: str
    context: str
    decision: str
    consequences: str
    alternatives: str
    status: str = "Accepted"


@dataclass
class Architecture:
    slug: str
    title: str
    summary: str
    diagrams: list[Diagram]
    decisions: list[Decision] = field(default_factory=list)


def _mm_text(s: str) -> str:
    return s.replace('"', "'").replace("\n", "<br/>")


def to_mermaid(d: Diagram) -> str:
    lines = [f"%% {d.title}", f"flowchart {d.direction}"]
    for gi, g in enumerate(d.columns):
        indent = "    "
        if g.label:
            gid = g.id or f"sg{gi}"
            lines.append(f'    subgraph {gid}["{_mm_text(g.label)}"]')
            lines.append("        direction TB")
            indent = "        "
        for n in g.nodes:
            o, c, _, _ = KINDS[n.kind]
            lines.append(f"{indent}{n.id}{o}{_mm_text(n.label)}{c}")
        if g.label:
            lines.append("    end")
    for e in d.edges:
        arrow = "-.->" if e.dashed else "-->"
        label = f'|"{_mm_text(e.label)}"|' if e.label else ""
        lines.append(f"    {e.src} {arrow}{label} {e.dst}")
    lines.append(MERMAID_CLASSES)
    by_class: dict[str, list[str]] = {}
    for n in d.nodes().values():
        by_class.setdefault(KINDS[n.kind][3], []).append(n.id)
    for cls, ids in sorted(by_class.items()):
        lines.append(f"    class {','.join(ids)} {cls}")
    return "\n".join(lines) + "\n"


NODE_W, NODE_H, GAP_Y, PAD, HEADER, GAP_X = 180, 64, 28, 20, 34, 90


def to_drawio(d: Diagram, name: str) -> str:
    """Editable diagrams.net XML: groups as swimlanes laid out in columns (LR) or rows (TB)."""
    cells = ['<mxCell id="0"/>', '<mxCell id="1" parent="0"/>']
    x = y = 40
    for gi, g in enumerate(d.columns):
        n = len(g.nodes)
        inner_w = NODE_W if d.direction == "LR" else n * NODE_W + (n - 1) * GAP_Y
        inner_h = n * NODE_H + (n - 1) * GAP_Y if d.direction == "LR" else NODE_H
        gw, gh = inner_w + 2 * PAD, inner_h + 2 * PAD + (HEADER if g.label else 0)
        parent = "1"
        ox, oy = x, y
        if g.label:
            gid = f"grp{gi}"
            cells.append(f'<mxCell id="{gid}" value="{html.escape(g.label, quote=True)}" '
                         f'style="swimlane;whiteSpace=wrap;html=1;rounded=1;startSize={HEADER};fillColor=#f9f9f9;'
                         f'strokeColor=#999999;fontStyle=1;" vertex="1" parent="1">'
                         f'<mxGeometry x="{x}" y="{y}" width="{gw}" height="{gh}" as="geometry"/></mxCell>')
            parent, ox, oy = gid, 0, 0
        for i, node in enumerate(g.nodes):
            nx = ox + PAD + (0 if d.direction == "LR" else i * (NODE_W + GAP_Y))
            ny = oy + PAD + (HEADER if g.label else 0) + (i * (NODE_H + GAP_Y) if d.direction == "LR" else 0)
            cells.append(f'<mxCell id="{node.id}" value="{html.escape(node.label, quote=True).replace(chr(10), "&lt;br&gt;")}" '
                         f'style="{KINDS[node.kind][2]}" vertex="1" parent="{parent}">'
                         f'<mxGeometry x="{nx}" y="{ny}" width="{NODE_W}" height="{NODE_H}" as="geometry"/></mxCell>')
        if d.direction == "LR":
            x += gw + GAP_X
        else:
            y += gh + GAP_X
    for i, e in enumerate(d.edges):
        style = "edgeStyle=orthogonalEdgeStyle;rounded=1;html=1;endArrow=block;endFill=1;" + ("dashed=1;" if e.dashed else "")
        cells.append(f'<mxCell id="e{i}" value="{html.escape(e.label, quote=True)}" style="{style}" edge="1" '
                     f'parent="1" source="{e.src}" target="{e.dst}"><mxGeometry relative="1" as="geometry"/></mxCell>')
    body = "\n        ".join(cells)
    return (f'<mxfile host="java-zero-hire" type="device">\n'
            f'  <diagram id="{name}" name="{html.escape(d.title, quote=True)}">\n'
            f'    <mxGraphModel dx="1400" dy="900" grid="1" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" '
            f'fold="1" page="1" pageScale="1" pageWidth="1654" pageHeight="1169" math="0" shadow="0">\n'
            f'      <root>\n        {body}\n      </root>\n    </mxGraphModel>\n  </diagram>\n</mxfile>\n')
