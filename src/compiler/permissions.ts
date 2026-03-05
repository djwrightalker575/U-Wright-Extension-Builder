import { BlockType, Project } from '../types/models';

export function computePermissions(project: Project) {
  const perms = new Set<string>();
  const hosts = new Set<string>();
  for (const t of project.targets) hosts.add(t);
  for (const n of project.workflow.nodes) {
    const type = (n.data as any).type as BlockType;
    if (type === BlockType.NOTIFY) perms.add('notifications');
    if (type === BlockType.TRIGGER_TOOLBAR_CLICK) {
      perms.add('scripting');
      perms.add('activeTab');
    }
  }
  const warnings = [...hosts].includes('<all_urls>') ? ['Avoid <all_urls> unless strictly required.'] : [];
  return { permissions: [...perms].sort(), host_permissions: [...hosts].sort(), warnings };
}
