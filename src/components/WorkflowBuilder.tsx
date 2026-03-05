import { useMemo } from 'react';
import ReactFlow, { Background, Controls, MiniMap, addEdge, Connection } from 'reactflow';
import 'reactflow/dist/style.css';
import { BlockType, Project } from '../types/models';

const blockOptions = Object.values(BlockType);

export function WorkflowBuilder({ project, onChange }: { project: Project; onChange: (project: Project) => void }) {
  const nodes = project.workflow.nodes;
  const edges = project.workflow.edges;

  const update = (nodesN = nodes, edgesN = edges) => onChange({ ...project, updatedAt: Date.now(), workflow: { nodes: nodesN, edges: edgesN } });
  const addBlock = (type: BlockType) => {
    const id = `${type}_${project.workflow.nodes.length + 1}`;
    update([...nodes, { id, type: 'default', position: { x: 90, y: 90 + nodes.length * 30 }, data: { type, label: type, settings: {} } }], edges);
  };

  const serialized = useMemo(() => JSON.stringify(project.workflow, null, 2), [project.workflow]);

  return (
    <div className="card">
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 8 }}>
        {blockOptions.map((b) => <button key={b} onClick={() => addBlock(b as BlockType)}>{b}</button>)}
      </div>
      <div className="reactflow-wrapper">
        <ReactFlow
          nodes={nodes}
          edges={edges}
          onNodesChange={(changes) => update(nodes.map((n) => changes.find((c) => c.id === n.id && 'position' in c)?.position ? { ...n, position: (changes.find((c) => c.id === n.id) as any).position } : n), edges)}
          onEdgesChange={() => {}}
          onConnect={(c: Connection) => update(nodes, addEdge(c, edges))}
        >
          <MiniMap /> <Controls /> <Background />
        </ReactFlow>
      </div>
      <details><summary>Workflow JSON</summary><pre>{serialized}</pre></details>
    </div>
  );
}
