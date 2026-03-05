import { z } from 'zod';
import { Edge, Node } from 'reactflow';

export enum BlockType {
  TRIGGER_TOOLBAR_CLICK = 'TRIGGER_TOOLBAR_CLICK',
  TRIGGER_PAGE_LOAD = 'TRIGGER_PAGE_LOAD',
  TRIGGER_URL_MATCH = 'TRIGGER_URL_MATCH',
  WAIT_FOR_SELECTOR = 'WAIT_FOR_SELECTOR',
  CLICK = 'CLICK',
  TYPE_TEXT = 'TYPE_TEXT',
  EXTRACT_TEXT = 'EXTRACT_TEXT',
  INJECT_CSS = 'INJECT_CSS',
  DOWNLOAD_TEXT_FILE = 'DOWNLOAD_TEXT_FILE',
  NOTIFY = 'NOTIFY',
  SET_VARIABLE = 'SET_VARIABLE',
  GET_VARIABLE = 'GET_VARIABLE',
  IF_ELSE = 'IF_ELSE',
  FOR_EACH = 'FOR_EACH',
  BUILD_CSV = 'BUILD_CSV'
}

export type ElementRef = { id: string; name: string; selector: string; textHint?: string; matchCount?: number };
export type BlockNodeData = { type: BlockType; label: string; settings: Record<string, unknown> };
export type WorkflowNode = Node<BlockNodeData>;
export type WorkflowEdge = Edge;

export const projectSchema = z.object({
  id: z.string(),
  name: z.string(),
  version: z.string().default('1.0.0'),
  description: z.string().default(''),
  createdAt: z.number(),
  updatedAt: z.number(),
  targets: z.array(z.string()).default(['https://example.com/*']),
  workflow: z.object({ nodes: z.array(z.any()), edges: z.array(z.any()) }),
  selectorLibrary: z.array(z.object({
    id: z.string(), name: z.string(), selector: z.string(), textHint: z.string().optional(), matchCount: z.number().optional()
  })).default([]),
  settings: z.object({ safeMode: z.boolean().default(true), allowRemoteFetch: z.boolean().default(false) })
});

export type Project = z.infer<typeof projectSchema>;
