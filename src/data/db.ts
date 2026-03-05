import Dexie, { type Table } from 'dexie';
import { Project, projectSchema } from '../types/models';

class FoundryDb extends Dexie {
  projects!: Table<Project, string>;

  constructor() {
    super('extension_foundry');
    this.version(1).stores({ projects: 'id, name, updatedAt' });
    this.version(2)
      .stores({ projects: 'id, name, updatedAt' })
      .upgrade(async (tx) => {
        const rows = await tx.table('projects').toArray();
        for (const row of rows) {
          const normalized = projectSchema.parse({ ...row, settings: row.settings ?? { safeMode: true, allowRemoteFetch: false } });
          await tx.table('projects').put(normalized);
        }
      });
  }
}

export const db = new FoundryDb();
