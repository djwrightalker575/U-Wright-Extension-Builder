import { BlockType, Project } from '../types/models';

const node = (id: string, type: BlockType, x: number, y: number, settings: Record<string, unknown> = {}) => ({
  id,
  position: { x, y },
  type: 'default',
  data: { type, label: type, settings }
});
const edge = (id: string, source: string, target: string) => ({ id, source, target });

export function templateMacro(base: Project): Project {
  return {
    ...base,
    name: 'Macro Automator',
    workflow: {
      nodes: [
        node('t1', BlockType.TRIGGER_PAGE_LOAD, 50, 80),
        node('w1', BlockType.WAIT_FOR_SELECTOR, 280, 80, { selector: '#actionBtn', timeoutMs: 3000 }),
        node('c1', BlockType.CLICK, 520, 80, { selector: '#actionBtn' }),
        node('ty1', BlockType.TYPE_TEXT, 760, 80, { selector: '#nameInput', text: 'Foundry' })
      ],
      edges: [edge('e1', 't1', 'w1'), edge('e2', 'w1', 'c1'), edge('e3', 'c1', 'ty1')]
    }
  };
}
export function templateHighlighter(base: Project): Project {
  return {
    ...base,
    name: 'Highlighter',
    workflow: { nodes: [node('t1', BlockType.TRIGGER_TOOLBAR_CLICK, 50, 80), node('i1', BlockType.INJECT_CSS, 320, 80, { cssText: '.foundry-highlight{outline:3px solid red;} #items li{outline:2px solid orange;}' })], edges: [edge('e1', 't1', 'i1')] }
  };
}
export function templateExtractor(base: Project): Project {
  return {
    ...base,
    name: 'Extractor to CSV',
    workflow: {
      nodes: [
        node('t1', BlockType.TRIGGER_PAGE_LOAD, 50, 80),
        node('x1', BlockType.EXTRACT_TEXT, 300, 80, { selector: '#items li', many: true }),
        node('csv1', BlockType.BUILD_CSV, 560, 80, { headers: 'Item' }),
        node('d1', BlockType.DOWNLOAD_TEXT_FILE, 820, 80, { filename: 'items.csv' })
      ],
      edges: [edge('e1', 't1', 'x1'), edge('e2', 'x1', 'csv1'), edge('e3', 'csv1', 'd1')]
    }
  };
}
