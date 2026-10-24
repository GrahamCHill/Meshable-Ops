import type { ComponentType } from 'react'
import type { PluginView } from '../api/plugins'

export interface WorkspacePluginModule {
  id: string
  apiVersion: 1
  loadPage: () => Promise<{ default: ComponentType<{plugin: PluginView}> }>
  loadExtensions?: Record<string, () => Promise<{default: ComponentType<{plugin: PluginView; slot: string; context: Record<string,unknown>}>}>>
}
export function extensionModule(plugin: PluginView, slot: string) {return plugin.source==='BUNDLED' && plugin.manifest.apiVersion===1 ? registry.get(plugin.manifest.id)?.loadExtensions?.[slot] : undefined}

// Bundled pages share the app runtime; uploaded pages use a separate sandboxed loader.
const sources = import.meta.glob<{ default: WorkspacePluginModule }>('./modules/*/index.ts', { eager: true })
const registry = new Map<string, WorkspacePluginModule>()
for (const { default: module } of Object.values(sources)) {
  if (!/^[a-z][a-z0-9-]{1,39}$/.test(module.id) || module.apiVersion !== 1 || registry.has(module.id)) throw new Error('Invalid or duplicate workspace plugin')
  registry.set(module.id, module)
}
export function pageModule(plugin: PluginView) {
  if (plugin.source === 'ZIP' && plugin.manifest.extensions.includes('PAGE') && plugin.manifest.apiVersion === 1) return runtimeModule
  const module = registry.get(plugin.manifest.id)
  return plugin.manifest.extensions.includes('PAGE') && module?.apiVersion === plugin.manifest.apiVersion ? module : undefined
}
const runtimeModule: WorkspacePluginModule = {id: 'runtime', apiVersion: 1, loadPage: () => import('./runtime/RuntimePluginPage')}
