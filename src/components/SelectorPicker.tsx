import { useRef, useState } from 'react';
import { ElementRef, Project } from '../types/models';

function cssPath(el: Element): string {
  if ((el as HTMLElement).id) return `#${(el as HTMLElement).id}`;
  const parts: string[] = [];
  let cur: Element | null = el;
  while (cur && cur.nodeType === 1 && cur.tagName.toLowerCase() !== 'html') {
    const tag = cur.tagName.toLowerCase();
    const idx = cur.parentElement ? [...cur.parentElement.children].filter((s) => s.tagName === cur!.tagName).indexOf(cur) + 1 : 1;
    parts.unshift(`${tag}:nth-of-type(${idx})`);
    cur = cur.parentElement;
  }
  return parts.join(' > ');
}

export function SelectorPicker({ project, onChange }: { project: Project; onChange: (p: Project) => void }) {
  const iframeRef = useRef<HTMLIFrameElement>(null);
  const [url, setUrl] = useState('/test_pages/simple.html');
  const [selected, setSelected] = useState('');
  const [count, setCount] = useState(0);

  const bindPicker = () => {
    const doc = iframeRef.current?.contentDocument;
    if (!doc) return;
    doc.onclick = (e) => {
      e.preventDefault();
      e.stopPropagation();
      const target = e.target as Element;
      const sel = cssPath(target);
      setSelected(sel);
      const c = doc.querySelectorAll(sel).length;
      setCount(c);
      highlight(sel);
    };
  };

  const highlight = (sel: string) => {
    const doc = iframeRef.current?.contentDocument;
    if (!doc) return;
    doc.querySelectorAll('.foundry-picked').forEach((n) => n.classList.remove('foundry-picked'));
    doc.querySelectorAll(sel).forEach((n) => n.classList.add('foundry-picked'));
    let style = doc.getElementById('foundry-picker-style');
    if (!style) {
      style = doc.createElement('style'); style.id = 'foundry-picker-style';
      style.textContent = '.foundry-picked{outline:3px solid #2563eb !important;}';
      doc.head.appendChild(style);
    }
  };

  const save = () => {
    if (!selected) return;
    const entry: ElementRef = { id: crypto.randomUUID(), name: `Selector ${project.selectorLibrary.length + 1}`, selector: selected, matchCount: count };
    onChange({ ...project, selectorLibrary: [...project.selectorLibrary, entry], updatedAt: Date.now() });
  };

  return <div className="card">
    <h3>Selector Picker</h3>
    <input value={url} onChange={(e) => setUrl(e.target.value)} />
    <div style={{ display: 'flex', gap: 8 }}>
      <button onClick={() => iframeRef.current && (iframeRef.current.src = url)}>Load URL</button>
      <button onClick={bindPicker}>Enable Picker</button>
      <button onClick={() => highlight(selected)}>Test selector</button>
      <button onClick={save}>Save ElementRef</button>
    </div>
    <div>Selected: <code>{selected || 'None'}</code> <span className="badge">matches: {count}</span></div>
    <iframe ref={iframeRef} title="test-page" src={url} sandbox="allow-same-origin allow-scripts allow-forms" style={{ width: '100%', height: 280, border: '1px solid #ddd' }} />
    <small>If cross-origin blocks access, use exported extension for two-phase picker mode via chrome.scripting.</small>
  </div>;
}
