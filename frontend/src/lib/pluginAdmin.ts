import type {PluginView} from '../api/plugins'

export function hasPluginSettings(plugin:PluginView) {
  return plugin.enabled && plugin.active && !plugin.removed && plugin.manifest.hasAdminSettings
}
export function hasEditablePluginSettings(plugin:PluginView) {
  const m=plugin.manifest
  return hasPluginSettings(plugin)&&!!(m.settingsFields.length||m.settingsSections?.length||m.sharedSettings?.length||m.sharedSettingsSchemas?.length)
}
export function pluginSettingsLocations(plugin:PluginView) {
  const m=plugin.manifest,sections=m.settingsSections??[],assigned=new Set(sections.flatMap(s=>s.fields))
  const main={id:'',category:m.adminMenu?.category??'workspace',categoryLabel:m.adminMenu?.categoryLabel??'Workspace',label:m.adminMenu?.label||m.name,description:m.description,fields:m.settingsFields.filter(f=>!assigned.has(f.key)).map(f=>f.key)}
  return [main,...sections]
}
export function pluginSettingsPath(plugin:PluginView,location=pluginSettingsLocations(plugin)[0]) {
  return `/admin/sections/${encodeURIComponent(location.category)}/plugins/${encodeURIComponent(plugin.manifest.id)}${location.id?`/${encodeURIComponent(location.id)}`:''}`
}
