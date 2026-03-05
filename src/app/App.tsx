import { useEffect, useState } from 'react';
import { db } from '../data/db';
import { Project } from '../types/models';
import { createBlankProject } from '../utils/project';
import { templateExtractor, templateHighlighter, templateMacro } from '../templates/templates';
import { WorkflowBuilder } from '../components/WorkflowBuilder';
import { SelectorPicker } from '../components/SelectorPicker';
import { TestHarness } from '../components/TestHarness';
import { exportDeterministicZip, triggerDownload } from '../exporter/zip';
import { compileProject } from '../compiler/compiler';

const tabs = ['Home', 'Project', 'Builder', 'Export'] as const;

export function App() {
  const [tab, setTab] = useState<(typeof tabs)[number]>('Home');
  const [projects, setProjects] = useState<Project[]>([]);
  const [currentId, setCurrentId] = useState<string | null>(null);
  const project = projects.find((p) => p.id === currentId) || null;
  const [warnings, setWarnings] = useState<string[]>([]);

  const load = async () => setProjects((await db.projects.orderBy('updatedAt').reverse().toArray()));
  useEffect(() => { load(); }, []);

  const save = async (p: Project) => { await db.projects.put(p); await load(); setCurrentId(p.id); };
  const create = async () => { const p = createBlankProject(); await save(p); };

  return <div className="layout">
    <aside className="sidebar">
      <h2>Extension Foundry</h2>
      {tabs.map((t) => <button key={t} onClick={() => setTab(t)}>{t}</button>)}
    </aside>
    <main className="main">
      {tab === 'Home' && <div className="card">
        <button className="primary" onClick={create}>Create Project</button>
        {projects.map((p) => <div key={p.id} style={{ display: 'flex', justifyContent: 'space-between', marginTop: 8 }}>
          <span>{p.name}</span>
          <span>
            <button onClick={() => setCurrentId(p.id)}>Open</button>
            <button onClick={async () => { const dup = { ...p, id: crypto.randomUUID(), name: `${p.name} Copy`, createdAt: Date.now(), updatedAt: Date.now() }; await save(dup); }}>Duplicate</button>
            <button onClick={async () => { await db.projects.delete(p.id); await load(); if (currentId === p.id) setCurrentId(null); }}>Delete</button>
          </span>
        </div>)}
      </div>}

      {tab === 'Project' && project && <div className="grid grid-2">
        <div className="card">
          <h3>Setup</h3>
          <label>Name<input value={project.name} onChange={(e) => save({ ...project, name: e.target.value, updatedAt: Date.now() })} /></label>
          <label>Version<input value={project.version} onChange={(e) => save({ ...project, version: e.target.value, updatedAt: Date.now() })} /></label>
          <label>Description<textarea value={project.description} onChange={(e) => save({ ...project, description: e.target.value, updatedAt: Date.now() })} /></label>
          <label>Targets (comma-separated)<input value={project.targets.join(',')} onChange={(e) => save({ ...project, targets: e.target.value.split(',').map((x) => x.trim()), updatedAt: Date.now() })} /></label>
        </div>
        <div className="card">
          <h3>Templates</h3>
          <button onClick={() => save(templateMacro({ ...project }))}>Macro Automator</button>
          <button onClick={() => save(templateHighlighter({ ...project }))}>Highlighter</button>
          <button onClick={() => save(templateExtractor({ ...project }))}>Extractor to CSV</button>
          <h4>Permissions preview</h4>
          <pre>{JSON.stringify(compileProject(project), null, 2)}</pre>
        </div>
      </div>}

      {tab === 'Builder' && project && <>
        <WorkflowBuilder project={project} onChange={save} />
        <SelectorPicker project={project} onChange={save} />
        <TestHarness project={project} />
      </>}

      {tab === 'Export' && project && <div className="card">
        <h3>Export deterministic MV3 ZIP</h3>
        <button className="primary" onClick={async () => {
          const result = await exportDeterministicZip(project);
          triggerDownload(`${project.name.replace(/\s+/g, '_')}.zip`, result.bytes);
          setWarnings(result.warnings);
        }}>Export ZIP</button>
        {!!warnings.length && <ul>{warnings.map((w) => <li key={w}>{w}</li>)}</ul>}
        <ol>
          <li>Unzip bundle.</li><li>Open chrome://extensions</li><li>Enable developer mode.</li><li>Load unpacked folder.</li>
        </ol>
      </div>}
      {!project && tab !== 'Home' && <div className="card">Select or create a project first.</div>}
    </main>
  </div>;
}
