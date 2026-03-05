import { Project } from '../types/models';

export type HarnessLog = { ts: number; message: string };

export class InAppRunner {
  private stopped = false;
  stop() { this.stopped = true; }
  reset() { this.stopped = false; }

  async run(project: Project, doc: Document, onLog: (log: HarnessLog) => void, step = false) {
    this.reset();
    const data: Record<string, unknown> = {};
    for (const node of project.workflow.nodes) {
      if (this.stopped) break;
      const settings = (node.data as any).settings || {};
      const type = (node.data as any).type;
      const log = (m: string) => onLog({ ts: Date.now(), message: `${type}: ${m}` });
      if (step) await new Promise((r) => setTimeout(r, 250));
      switch (type) {
        case 'WAIT_FOR_SELECTOR': await waitForSelector(doc, String(settings.selector), Number(settings.timeoutMs ?? 3000)); log('ok'); break;
        case 'CLICK': (doc.querySelector(String(settings.selector)) as HTMLElement | null)?.click(); log('clicked'); break;
        case 'TYPE_TEXT': {
          const el = doc.querySelector(String(settings.selector)) as HTMLInputElement | null;
          if (el) { el.value = String(settings.text ?? ''); el.dispatchEvent(new Event('input', { bubbles: true })); }
          log('typed');
          break;
        }
        case 'EXTRACT_TEXT': {
          const many = Boolean(settings.many);
          data.last = many
            ? [...doc.querySelectorAll(String(settings.selector))].map((x) => x.textContent?.trim() ?? '')
            : doc.querySelector(String(settings.selector))?.textContent?.trim() ?? '';
          log('extracted');
          break;
        }
        case 'INJECT_CSS': {
          const st = doc.createElement('style'); st.textContent = String(settings.cssText ?? ''); doc.head.appendChild(st); log('css'); break;
        }
        case 'BUILD_CSV': {
          const headers = String(settings.headers ?? 'Value');
          const arr = Array.isArray(data.last) ? data.last : [String(data.last ?? '')];
          data.last = [headers, ...arr.map((x) => JSON.stringify(x))].join('\n'); log('csv'); break;
        }
        case 'DOWNLOAD_TEXT_FILE': data.lastDownload = { filename: settings.filename ?? 'out.txt', content: String(data.last ?? settings.content ?? '') }; log('download mocked'); break;
        default: log('skipped');
      }
    }
    return data;
  }
}

async function waitForSelector(doc: Document, selector: string, timeoutMs: number) {
  const started = Date.now();
  while (!doc.querySelector(selector)) {
    if (Date.now() - started > timeoutMs) throw new Error(`Timeout waiting for ${selector}`);
    await new Promise((r) => setTimeout(r, 100));
  }
}
