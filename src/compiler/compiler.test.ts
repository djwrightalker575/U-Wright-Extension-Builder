import { describe, expect, it } from 'vitest';
import { createBlankProject } from '../utils/project';
import { templateMacro } from '../templates/templates';
import { compileProject } from './compiler';

describe('compileProject', () => {
  it('emits MV3 manifest and required files', () => {
    const project = templateMacro(createBlankProject());
    const { files } = compileProject(project);
    const manifest = files.find((f) => f.path === 'extension/manifest.json');
    expect(manifest).toBeTruthy();
    expect(manifest?.content).toContain('"manifest_version": 3');
    expect(files.some((f) => f.path === 'extension/generated/runner.js')).toBe(true);
    expect(files.some((f) => f.path === 'extension/runtime/runtime.js')).toBe(true);
  });
});
