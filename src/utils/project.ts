import { Project } from '../types/models';

export function createBlankProject(name = 'New Project'): Project {
  const now = Date.now();
  return {
    id: crypto.randomUUID(),
    name,
    version: '1.0.0',
    description: '',
    createdAt: now,
    updatedAt: now,
    targets: ['http://localhost:4173/*'],
    workflow: { nodes: [], edges: [] },
    selectorLibrary: [],
    settings: { safeMode: true, allowRemoteFetch: false }
  };
}
