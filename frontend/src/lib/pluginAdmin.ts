import type {PluginView} from '../api/plugins'

export function hasPluginSettings(plugin:PluginView) {
  return plugin.enabled && plugin.active && !plugin.removed && plugin.manifest.hasAdminSettings
}
export function pluginSettingsPath(plugin:PluginView) {
  return `/admin/sections/${encodeURIComponent(plugin.manifest.adminMenu?.category ?? 'workspace')}/plugins/${encodeURIComponent(plugin.manifest.id)}`
}
