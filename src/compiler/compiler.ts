import { Project } from '../types/models';
import { computePermissions } from './permissions';
import { stableStringify } from './deterministic';

export type CompiledFile = { path: string; content: string };

const runtimeCode = `export const runtime = {
  waitForSelector: (selector, timeoutMs=3000)=>new Promise((resolve,reject)=>{const start=Date.now();const t=setInterval(()=>{const el=document.querySelector(selector);if(el){clearInterval(t);resolve(el);}else if(Date.now()-start>timeoutMs){clearInterval(t);reject(new Error('timeout'));}},100);}),
  click: (selector)=>document.querySelector(selector)?.dispatchEvent(new MouseEvent('click',{bubbles:true})),
  typeText: (selector,text)=>{const el=document.querySelector(selector);if(el){el.value=text;el.dispatchEvent(new Event('input',{bubbles:true}));}},
  extractText: (selector,many=false)=>many?[...document.querySelectorAll(selector)].map(x=>x.textContent?.trim()??''):document.querySelector(selector)?.textContent?.trim()??'',
  injectCss: (css)=>{const s=document.createElement('style');s.textContent=css;document.head.appendChild(s);},
  downloadText: (filename,content)=>{const b=new Blob([content],{type:'text/plain'});const a=document.createElement('a');a.href=URL.createObjectURL(b);a.download=filename;a.click();URL.revokeObjectURL(a.href);}
};`;

function runnerCode(project: Project) {
  return `import { runtime } from '../runtime/runtime.js';\nexport const workflow=${stableStringify(project.workflow)};\nexport async function run(log){const data={};for(const node of workflow.nodes){const s=node.data.settings||{};switch(node.data.type){case 'WAIT_FOR_SELECTOR': await runtime.waitForSelector(s.selector,s.timeoutMs||3000);log('wait '+s.selector);break;case 'CLICK': runtime.click(s.selector);log('click '+s.selector);break;case 'TYPE_TEXT': runtime.typeText(s.selector,s.text||'');log('type '+s.selector);break;case 'EXTRACT_TEXT': data.last=runtime.extractText(s.selector,!!s.many);log('extract '+s.selector);break;case 'INJECT_CSS': runtime.injectCss(s.cssText||'');log('inject css');break;case 'BUILD_CSV': {const list=Array.isArray(data.last)?data.last:[String(data.last??'')];const h=(s.headers||'Value').split(',');data.last=[h.join(','),...list.map((v)=>JSON.stringify(v))].join('\\n');log('build csv');break;}case 'DOWNLOAD_TEXT_FILE': runtime.downloadText(s.filename||'output.txt',String(data.last??s.content??''));log('download');break;case 'NOTIFY': if(globalThis.chrome?.notifications){chrome.notifications.create({type:'basic',iconUrl:'icons/icon128.png',title:s.title||'Notice',message:s.message||''});}log('notify');break;default: log('skip '+node.data.type);}}return data;}`;
}

export function compileProject(project: Project): { files: CompiledFile[]; warnings: string[] } {
  const p = computePermissions(project);
  const manifest = {
    manifest_version: 3,
    name: project.name,
    version: project.version,
    description: project.description,
    permissions: p.permissions,
    host_permissions: p.host_permissions,
    action: { default_popup: 'popup/popup.html' },
    background: { service_worker: 'background/sw.js', type: 'module' },
    content_scripts: [{ matches: project.targets, js: ['content/content.js'] }]
  };

  const files: CompiledFile[] = [
    { path: 'extension/README_INSTALL.txt', content: 'Load as unpacked extension in chrome://extensions' },
    { path: 'extension/manifest.json', content: stableStringify(manifest) },
    { path: 'extension/runtime/runtime.js', content: runtimeCode },
    { path: 'extension/generated/workflow.json', content: stableStringify(project.workflow) },
    { path: 'extension/generated/runner.js', content: runnerCode(project) },
    { path: 'extension/content/content.js', content: "import { run } from '../generated/runner.js'; window.__foundryRun=()=>run(console.log);" },
    { path: 'extension/background/sw.js', content: "chrome.action.onClicked.addListener(async(tab)=>{if(!tab.id) return;await chrome.scripting.executeScript({target:{tabId:tab.id},func:()=>window.__foundryRun?.()});});" },
    { path: 'extension/popup/popup.html', content: '<!doctype html><html><body><button id="run">Run</button><script type="module" src="popup.js"></script></body></html>' },
    { path: 'extension/popup/popup.js', content: "document.getElementById('run').addEventListener('click',()=>chrome.tabs.query({active:true,currentWindow:true},([tab])=>{if(tab?.id)chrome.scripting.executeScript({target:{tabId:tab.id},func:()=>window.__foundryRun?.()});}));" },
    { path: 'extension/options/options.html', content: '<!doctype html><html><body>Extension Foundry MVP</body></html>' }
  ];

  return { files: files.sort((a, b) => a.path.localeCompare(b.path)), warnings: p.warnings };
}
