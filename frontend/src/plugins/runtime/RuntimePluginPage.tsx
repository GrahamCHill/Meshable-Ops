import { useLayoutEffect, useMemo, useRef } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import { pluginsApi, type PluginView } from '../../api/plugins'
import { getErrorMessage } from '../../components/admin/adminUtils'
import { dispatchPluginRequest, isPluginRequest } from './bridge'
import { runtimeDocument } from './document'
import { useAuthStore } from '../../hooks/useAuthStore'
import { pluginRole } from '../../api/access'
import { projectsApi } from '../../api/projects'

export default function RuntimePluginPage({ plugin }: { plugin: PluginView }) {
  const { '*': section = '' } = useParams(); const navigate = useNavigate(); const location = useLocation()
  const frame = useRef<HTMLIFrameElement>(null)
  const role = useAuthStore(state => pluginRole(state.user?.role ?? ''))
  const projectId = plugin.manifest.contextTargets?.includes('PROJECT') ? new URLSearchParams(location.search).get('project') : null
  const clientId = plugin.manifest.contextTargets?.includes('CLIENT') ? new URLSearchParams(location.search).get('client') : null
  const projectContext = useQuery({queryKey: ['plugin-project-context', projectId], queryFn: () => projectsApi.get(projectId!), enabled: !!projectId, retry: false})
  const clientContext = useQuery({queryKey: ['plugin-client-context', clientId], queryFn: () => projectsApi.client(clientId!), enabled: !!clientId, retry: false})
  const runtime = useQuery({queryKey: ['plugin-runtime', plugin.manifest.id, section], queryFn: () => pluginsApi.runtime(plugin.manifest.id, section), refetchInterval: 15_000, retry: false})
  const channel = useMemo(() => crypto.randomUUID(), [plugin.manifest.id, section, runtime.data, runtime.isError, projectContext.data, clientContext.data])
  const document = useMemo(() => runtime.data ? runtimeDocument(runtime.data.html, channel) : '', [runtime.data, channel])
  const context = useMemo(() => ({plugin: {id: plugin.manifest.id, name: plugin.manifest.name}, section, role,
    configuration: runtime.data?.configuration ?? {}, tables: plugin.manifest.tables, tableAccess: plugin.tableAccess, project: projectContext.data ?? null, client: clientContext.data ?? null,
    record: new URLSearchParams(location.search).get('record'), table: new URLSearchParams(location.search).get('table')}), [plugin, section, role, runtime.data, location.search, projectContext.data, clientContext.data])
  // Keep the port alive across harmless query refreshes; each request reads the current context.
  const current = useRef({plugin, context}); current.current = {plugin, context}
  useLayoutEffect(() => {
    let port: MessagePort | undefined; let active = true; let inFlight = 0; let requests = 0; let windowStart = Date.now()
    const connect = (event: MessageEvent) => {
      if (port || event.source !== frame.current?.contentWindow || event.origin !== 'null' || event.data?.type !== 'weaveops:connect' || event.data.channel !== channel || event.ports.length !== 1) return
      port = event.ports[0]
      port.onmessage = async event => {
        if (!active || !isPluginRequest(event.data)) return
        const request = event.data
        if (Date.now() - windowStart > 60000) {requests = 0; windowStart = Date.now()}
        if (++requests > 120 || inFlight >= 8) {port?.postMessage({id: request.id, error: 'Too many plugin requests. Try again shortly.'}); return}
        inFlight++
        try {
          const result = await dispatchPluginRequest(request, current.current.plugin, current.current.context,
            next => navigate(`/plugins/${plugin.manifest.id}${next ? `/${next}` : ''}${location.search}`))
          if (active) port?.postMessage({id: request.id, result})
        } catch (error) { if (active) port?.postMessage({id: request.id, error: getErrorMessage(error, error instanceof Error ? error.message : 'Plugin request failed.')}) }
        finally {inFlight--}
      }
      port.start()
    }
    window.addEventListener('message', connect)
    return () => {active = false; window.removeEventListener('message', connect); port?.close()}
  }, [channel, navigate, plugin.manifest.id, location.search])
  if (projectContext.isError || clientContext.isError) return <p role="alert">This extension's project or client context is unavailable.</p>
  if (projectId && projectContext.isLoading || clientId && clientContext.isLoading) return <p role="status">Checking extension context access…</p>
  if (runtime.isError) return <p role="alert">{getErrorMessage(runtime.error, 'This extension is unavailable.')} <button onClick={() => void runtime.refetch()}>Retry</button></p>
  if (!runtime.data) return <p role="status">Loading extension…</p>
  return <div className="page-stack"><div><h1>{plugin.manifest.name}</h1><p>{plugin.manifest.description}</p></div>
    <iframe key={channel} ref={frame} title={`${plugin.manifest.name} extension`} sandbox="allow-scripts allow-forms" referrerPolicy="no-referrer"
      allow="camera 'none'; microphone 'none'; geolocation 'none'; clipboard-read 'none'; clipboard-write 'none'" srcDoc={document}
      style={{width: '100%', minHeight: 650, border: 0, background: '#fff'}} />
  </div>
}
