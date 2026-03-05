import JSZip from 'jszip';
import { compileProject } from '../compiler/compiler';
import { FIXED_DATE } from '../compiler/deterministic';
import { Project } from '../types/models';

export async function exportDeterministicZip(project: Project) {
  const { files, warnings } = compileProject(project);
  const zip = new JSZip();
  for (const file of files) {
    zip.file(file.path, file.content, { date: FIXED_DATE });
  }
  const bytes = await zip.generateAsync({ type: 'uint8array', compression: 'DEFLATE', compressionOptions: { level: 9 } });
  return { bytes, warnings };
}

export function triggerDownload(filename: string, bytes: Uint8Array) {
  const blob = new Blob([bytes], { type: 'application/zip' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = filename;
  a.click();
  URL.revokeObjectURL(a.href);
}
