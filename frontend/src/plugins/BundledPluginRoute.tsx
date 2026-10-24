import {lazy, Suspense, useMemo} from 'react'
import {Link} from 'react-router-dom'
import {useAvailablePlugins} from '../api/plugins'
import {useAccess} from '../api/access'
import {bundledRoutes} from './registry'

export function BundledPluginRoute({route}: {route: typeof bundledRoutes[number]}) {
  const plugins = useAvailablePlugins()
  const access = useAccess()
  const Page = useMemo(() => lazy(route.loadPage), [route.loadPage])
  const plugin = plugins.data?.find(value => value.manifest.id === route.pluginId && value.source === 'BUNDLED')
  if (plugins.isLoading) return <p role="status">Loading plugin…</p>
  if (plugins.isError) return <p role="alert">Unable to check plugin access. <button onClick={() => void plugins.refetch()}>Retry</button></p>
  if (!plugin || !access.can(route.feature)) return <div className="page-stack"><h1>Plugin unavailable</h1><p>This optional component is disabled or restricted.</p><Link to="/">Back to workspace</Link></div>
  return <Suspense fallback={<p role="status">Loading {route.label.toLowerCase()}…</p>}><Page/></Suspense>
}
