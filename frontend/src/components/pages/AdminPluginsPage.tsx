import { useQuery } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router-dom'
import { pluginsApi } from '../../api/plugins'
import { AdminPageHeader } from '../admin/AdminPageHeader'
import { PluginSetting } from '../admin/plugins/PluginSetting'
import { PluginConfigurationForm } from '../admin/plugins/PluginConfigurationForm'
import { PluginDataControls } from '../admin/plugins/PluginDataControls'
import { PluginConnections } from '../admin/plugins/PluginConnections'
import { useState } from 'react'
import { PluginInstaller } from '../admin/plugins/PluginInstaller'
import { MessagingMembers } from '../admin/plugins/MessagingMembers'

export function AdminPluginsPage() {
  const plugins = useQuery({ queryKey: ['admin-plugins'], queryFn: pluginsApi.installed })
  const [params, setParams] = useSearchParams()
  const [filter, setFilter] = useState('')
  const selected = plugins.data?.find(plugin => plugin.manifest.id === params.get('plugin'))
  const tab = params.get('tab') ?? 'general'
  const tabs = [{id:'general',label:'General'}, {id:'configuration',label:'Configuration'}, {id:'connections',label:'Connections'}, ...(['discord', 'telegram', 'teams'].includes(selected?.manifest.id ?? '') ? [{id:'members', label:'People & chats'}] : []), ...(selected?.manifest.tables.length ? [{id:'data',label:'Data'}] : [])]
  return <div className="page-stack" style={{ maxWidth: selected ? 760 : 1000 }}>
    <AdminPageHeader title={selected ? `${selected.manifest.name} settings` : 'Plugins'} description={selected ? selected.manifest.description : 'Manage optional pages, connections, database access, and search.'} />
    {plugins.isLoading && <p role="status">Loading plugins…</p>}
    {plugins.isError && <p role="alert">Unable to load plugins. <button onClick={() => void plugins.refetch()}>Retry</button></p>}
    {params.has('plugin') && !selected && !plugins.isLoading && !plugins.isError && <p role="alert">This plugin is not installed. <Link to="/admin/plugins">Back to plugins</Link></p>}
    {selected ? <>
      <Link to="/admin/plugins">Back to plugins</Link>
      {selected.blockedBySecurity && <p role="status">This uploaded extension is blocked by the global third-party extensions setting. A super admin can change this in Security.</p>}
      {selected.source === 'ZIP' && <div><p className="field-help">Uploaded extension · Author: {selected.author?.name || 'Not provided'} · Organisation: {selected.author?.organization || 'Not provided'}</p><p className="field-help">Email: {selected.author?.email || 'Not provided'} · Website: {selected.author?.website || 'Not provided'}</p></div>}
      <nav className="workspace-tabs" aria-label="Plugin settings">{tabs.map(item => <button key={item.id} aria-current={tab === item.id ? 'page' : undefined} className={tab === item.id ? 'is-active' : ''} onClick={() => setParams({plugin:selected.manifest.id,tab:item.id})}>{item.label}</button>)}</nav>
      {tab === 'configuration' ? <PluginConfigurationForm key={selected.manifest.id} plugin={selected} /> : tab === 'members' && ['discord', 'telegram', 'teams'].includes(selected.manifest.id) ? <MessagingMembers key={selected.manifest.id} pluginId={selected.manifest.id} /> : tab === 'connections' ? <PluginConnections plugin={selected} /> : tab === 'data' && selected.manifest.tables.length ? <PluginDataControls plugin={selected} /> : <PluginSetting key={selected.manifest.id} plugin={selected} />}
    </> : !params.has('plugin') && <>
      <p className="field-help">New plugins start disabled and excluded from search. Each plugin has its own settings menu. Disabling retains saved data and connection settings.</p>
      <PluginInstaller />
      <label className="form-field">Find a plugin<input type="search" value={filter} onChange={event => setFilter(event.target.value)} /></label>
      <div className="admin-cards">{plugins.data?.filter(plugin => `${plugin.manifest.name} ${plugin.manifest.description}`.toLowerCase().includes(filter.toLowerCase())).map(plugin => <section key={plugin.manifest.id} className="page-stack">
        <div><h2>{plugin.manifest.name}</h2><p className="field-help">{plugin.blockedBySecurity ? 'Blocked by security' : plugin.active ? 'Enabled' : plugin.enabled ? 'Waiting for support' : 'Disabled'} · Version {plugin.manifest.version} · {plugin.source === 'ZIP' ? 'Uploaded ZIP' : 'Bundled'}</p></div>
        <p>{plugin.manifest.description}</p>
        <Link className="button button-secondary" aria-label={`Settings for ${plugin.manifest.name}`} to={`/admin/plugins?plugin=${encodeURIComponent(plugin.manifest.id)}`}>Settings</Link>
      </section>)}</div>
      {plugins.data?.length === 0 && <p>No plugins are included in this build.</p>}
    </>}
  </div>
}
