import type { ComponentType } from 'react'
import type { PluginView } from '../api/plugins'

export interface WorkspacePluginModule {
  id: string
  apiVersion: 1
  loadPage: () => Promise<{ default: ComponentType }>
}

// Only source modules included in this build are executable. Server metadata never supplies JavaScript.
const sources = import.meta.glob<{ default: WorkspacePluginModule }>('./modules/*/index.ts', { eager: true })
const registry = new Map<string, WorkspacePluginModule>()
for (const { default: module } of Object.values(sources)) {
  if (!/^[a-z][a-z0-9-]{1,39}$/.test(module.id) || module.apiVersion !== 1 || registry.has(module.id)) throw new Error('Invalid or duplicate workspace plugin')
  registry.set(module.id, module)
}
export function pageModule(plugin: PluginView) {
  const module = registry.get(plugin.manifest.id)
  return plugin.manifest.extensions.includes('PAGE') && module?.apiVersion === plugin.manifest.apiVersion ? module : undefined
}
