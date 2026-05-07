import { useMemo } from "react";
import ReactFlow, {
  Background,
  Controls,
  MarkerType,
  type Edge,
  type Node,
} from "reactflow";
import type { DependencyNode, HealthInfo, HealthStatus } from "../types";
import { statusColor } from "../util";

interface Props {
  tree: DependencyNode;
  healthByGav: Record<string, HealthInfo>;
  onNodeClick: (info: HealthInfo) => void;
}

const NODE_WIDTH = 220;
const NODE_HEIGHT = 60;
const X_GAP = 60;
const Y_GAP = 14;

interface Layout {
  nodes: Node[];
  edges: Edge[];
}

export function TreeGraph({ tree, healthByGav, onNodeClick }: Props) {
  const layout = useMemo<Layout>(
    () => buildLayout(tree, healthByGav),
    [tree, healthByGav],
  );

  return (
    <div className="h-[640px] w-full rounded-lg border border-slate-800 bg-slate-950">
      <ReactFlow
        nodes={layout.nodes}
        edges={layout.edges}
        fitView
        nodesDraggable={false}
        nodesConnectable={false}
        elementsSelectable
        proOptions={{ hideAttribution: true }}
        onNodeClick={(_, n) => {
          const info = (n.data as { info?: HealthInfo }).info;
          if (info) onNodeClick(info);
        }}
      >
        <Background color="#1e293b" gap={24} />
        <Controls showInteractive={false} />
      </ReactFlow>
    </div>
  );
}

interface Pos {
  x: number;
  y: number;
}

function buildLayout(
  root: DependencyNode,
  health: Record<string, HealthInfo>,
): Layout {
  const nodes: Node[] = [];
  const edges: Edge[] = [];
  const seen = new Set<string>();

  // First pass: count leaves at each subtree to compute vertical spacing
  function leafCount(n: DependencyNode): number {
    if (n.children.length === 0) return 1;
    return n.children.reduce((s, c) => s + leafCount(c), 0);
  }

  let yCursor = 0;

  function place(n: DependencyNode, depth: number, parentId: string | null) {
    const gav = gavOf(n);
    const id = `${gav}@${depth}-${nodes.length}`;
    const myLeaves = leafCount(n);
    const yStart = yCursor;
    if (n.children.length === 0) {
      yCursor += NODE_HEIGHT + Y_GAP;
    } else {
      n.children.forEach((c) => place(c, depth + 1, id));
    }
    const yEnd = n.children.length === 0
      ? yStart
      : yCursor - (NODE_HEIGHT + Y_GAP);
    const y = (yStart + yEnd) / 2;

    const info = health[gav];
    const status: HealthStatus = info?.status ?? "UNKNOWN";

    if (!seen.has(id)) {
      seen.add(id);
      nodes.push({
        id,
        position: { x: depth * (NODE_WIDTH + X_GAP), y },
        data: {
          info,
          label: (
            <div className="flex flex-col text-left">
              <div className="flex items-center gap-1.5">
                <span
                  className={`inline-block h-2 w-2 rounded-full ${statusColor(
                    status,
                  )}`}
                />
                <span className="truncate font-medium text-slate-100">
                  {n.coordinate.artifactId}
                </span>
              </div>
              <span className="truncate text-[10px] text-slate-400">
                {n.coordinate.version}
              </span>
            </div>
          ),
        },
        style: {
          width: NODE_WIDTH,
          height: NODE_HEIGHT,
          padding: 8,
          borderRadius: 6,
          fontSize: 12,
        },
        sourcePosition: "right" as any,
        targetPosition: "left" as any,
      });
    }
    if (parentId) {
      edges.push({
        id: `${parentId}->${id}`,
        source: parentId,
        target: id,
        markerEnd: { type: MarkerType.ArrowClosed, color: "#64748b" },
      });
    }
    if (myLeaves === 1 && n.children.length === 0) {
      // already advanced yCursor
    }
  }

  place(root, 0, null);
  return { nodes, edges };
}

function gavOf(n: DependencyNode): string {
  return `${n.coordinate.groupId}:${n.coordinate.artifactId}:${n.coordinate.version}`;
}
