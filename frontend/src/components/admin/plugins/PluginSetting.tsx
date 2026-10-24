import { useEffect, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { pluginsApi, type PluginView, type PluginSettings } from '../../../api/plugins'
import { pageModule } from '../../../plugins/registry'
import { getErrorMessage } from '../adminUtils'
import { Button } from '../../ui'

const settingsFor = (plugin: PluginView): PluginSettings => ({enabled: plugin.enabled, allowedRoles: plugin.allowedRoles, searchEnabled: plugin.searchEnabled, tableAccess: plugin.tableAccess})
export function PluginSetting({ plugin }: { plugin: PluginView }) {
  const client = useQueryClient()
  const [settings, setSettings] = useState(() => settingsFor(plugin))
  useEffect(() => setSettings(settingsFor(plugin)), [plugin])
  const save = useMutation({ mutationFn: () => pluginsApi.configure(plugin.manifest.id, settings), onSuccess: async () => {
    await Promise.all([client.invalidateQueries({ queryKey: ['admin-plugins'] }), client.invalidateQueries({ queryKey: ['plugins'] }),client.invalidateQueries({queryKey:['my-access']}),client.invalidateQueries({queryKey:['admin-notifications']}),client.invalidateQueries({queryKey:['notification-preferences']}),client.invalidateQueries({queryKey:['admin-navigation']}),client.invalidateQueries({queryKey:['workspace-navigation']}),client.invalidateQueries({queryKey:['admin-section-artwork']}),client.invalidateQueries({queryKey:['admin-section-icons']})])
  } })
  const update = (values: Partial<PluginSettings>) => {setSettings(current => ({...current, ...values})); save.reset()}
  const tableAccess = (name: string, level: string) => update({tableAccess: {...settings.tableAccess, [name]: {
    readRoles: level === 'admin' ? ['ADMIN'] : ['ADMIN', 'USER'], writeRoles: level === 'write' ? ['ADMIN', 'USER'] : ['ADMIN'],
  }}})
  return <section className="page-stack" aria-label={plugin.manifest.name}>
    <p className="field-help">Version {plugin.manifest.version} · {plugin.manifest.extensions.join(', ').toLowerCase()}</p>
    {plugin.manifest.extensions.includes('PAGE') && !pageModule(plugin) && <p role="alert">The page module is missing or incompatible. Rebuild the frontend with this plugin before enabling it.</p>}
    <fieldset disabled={save.isPending} className="page-stack">
      <label className="checkbox-label"><input type="checkbox" checked={settings.enabled} onChange={event => update({enabled:event.target.checked})} />Enabled</label>
      {plugin.active?<><label className="form-field">Plugin access<select value={settings.allowedRoles.includes('USER') ? 'all' : 'admin'} onChange={event => update({allowedRoles:event.target.value === 'all' ? ['ADMIN', 'USER'] : ['ADMIN']})}><option value="admin">Administrators</option>{plugin.manifest.supportedRoles.includes('USER') && <option value="all">All signed-in users</option>}</select></label>
      {plugin.manifest.extensions.includes('SEARCH') && <label className="checkbox-label"><input type="checkbox" checked={settings.searchEnabled} onChange={event => update({searchEnabled:event.target.checked})} />Include in workspace search</label>}
      {plugin.manifest.tables.length > 0 && <div className="page-stack"><h3>Database access</h3><p className="field-help">Administrators retain read and write access. Member access also requires the plugin to be enabled for all signed-in users. Record ownership still applies.</p>{plugin.manifest.tables.map(table => {
        const access = settings.tableAccess[table.name]
        const level = access?.writeRoles.includes('USER') ? 'write' : access?.readRoles.includes('USER') ? 'read' : 'admin'
        return <label className="form-field" key={table.name}>{table.label}<small>{table.name}</small><select value={level} onChange={event => tableAccess(table.name, event.target.value)}><option value="admin">Administrators only</option><option value="read">Members can read</option><option value="write">Members can read and write</option></select></label>
      })}</div>}
      </>:<p>Enable this plugin to access its settings. Existing configuration and data are retained.</p>}
      <div><Button disabled={save.isPending || JSON.stringify(settings) === JSON.stringify(settingsFor(plugin))} onClick={() => save.mutate()}>Save plugin settings</Button></div>
    </fieldset>
    {save.isError && <p className="feedback error" role="alert">{getErrorMessage(save.error)}</p>}
    {save.isSuccess && <p role="status">Plugin settings saved.</p>}
  </section>
}
