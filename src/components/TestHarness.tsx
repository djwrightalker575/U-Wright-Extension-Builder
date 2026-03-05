import { useRef, useState } from 'react';
import { Project } from '../types/models';
import { InAppRunner, HarnessLog } from '../harness/runner';

export function TestHarness({ project }: { project: Project }) {
  const iframeRef = useRef<HTMLIFrameElement>(null);
  const runnerRef = useRef(new InAppRunner());
  const [logs, setLogs] = useState<HarnessLog[]>([]);

  const append = (log: HarnessLog) => setLogs((prev) => [...prev, log]);
  const run = async (step = false) => {
    const doc = iframeRef.current?.contentDocument;
    if (!doc) return;
    setLogs([]);
    await runnerRef.current.run(project, doc, append, step);
  };

  return <div className="card">
    <h3>Test Harness</h3>
    <div style={{ display: 'flex', gap: 8 }}>
      <button className="primary" onClick={() => run(false)}>Run</button>
      <button onClick={() => run(true)}>Step Mode</button>
      <button onClick={() => runnerRef.current.stop()}>Stop</button>
    </div>
    <iframe ref={iframeRef} src="/test_pages/simple.html" title="harness" style={{ width: '100%', height: 240, border: '1px solid #ddd', marginTop: 8 }} />
    <div className="logs">{logs.map((l) => <div key={l.ts + l.message}>[{new Date(l.ts).toLocaleTimeString()}] {l.message}</div>)}</div>
  </div>;
}
