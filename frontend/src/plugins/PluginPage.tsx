import { pluginRole } from '../api/access'
import { Component, lazy, Suspense, useMemo, type ReactNode } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useAvailablePlugins } from '../api/plugins'
import { pageModule } from './registry'
import { useAuthStore } from '../hooks/useAuthStore'

class PluginErrorBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false }
  static getDerivedStateFromError() { return { failed: true } }
  render() { return this.state.failed ? <p role="alert">This plugin could not load. Reload the page to retry.</p> : this.props.children }
}
export default function PluginPage() {
  const { pluginId, '*': childPath = '' } = useParams()
  const role = useAuthStore(state => pluginRole(state.user?.role ?? ''))
  const plugins = useAvailablePlugins()
  const plugin = plugins.data?.find(item => item.manifest.id === pluginId)
  const module = plugin && pageModule(plugin)
  const Page = useMemo(() => module ? lazy(module.loadPage) : undefined, [module])
  if (plugins.isLoading) return <p role="status">Loading plugin…</p>
  if (plugins.isError) return <p role="alert">Unable to check plugin access. <button onClick={() => void plugins.refetch()}>Retry</button></p>
  if (!Page || !plugin) return <div className="page-stack"><h1>Plugin unavailable</h1><p>This plugin is disabled, restricted, or missing from this app build.</p><Link to="/">Back to workspace</Link></div>
  const section = plugin?.manifest.menuItems.find(item => item.path === childPath.split('/')[0])
  if (childPath && (!section || !role || !section.roles.includes(role))) return <p role="alert">This plugin section is unavailable.</p>
  return <PluginErrorBoundary key={pluginId}><div className="page-stack">{plugin.manifest.banner&&<img className="plugin-banner" src={`/api/plugins/${pluginId}/assets/banner`} alt="" onError={event=>{event.currentTarget.hidden=true}}/>}<Suspense fallback={<p role="status">Loading plugin…</p>}><Page plugin={plugin} /></Suspense></div></PluginErrorBoundary>
}
