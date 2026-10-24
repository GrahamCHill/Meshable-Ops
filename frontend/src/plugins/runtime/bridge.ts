import api from '../../api/http'
import type { PluginView } from '../../api/plugins'

interface Request { id: string; method: string; params: Record<string, unknown> }
export async function dispatchPluginRequest(request: Request, plugin: PluginView, context: Record<string, unknown>, navigate: (section: string) => void) {
  const { method, params } = request
  if (method === 'context') return context
  if (method === 'navigate') {
    if (typeof params.section !== 'string' || params.section !== '' && !plugin.manifest.menuItems.some(item => item.path === params.section)) throw new Error('Choose a declared plugin section.')
    navigate(params.section); return null
  }
  if (method === 'backend') {
    if (!plugin.manifest.extensions.includes('API') || typeof params.path !== 'string' || !/^\/[a-z][a-z0-9-]*(\/[a-z][a-z0-9-]*){0,3}$/.test(params.path) || !['GET','POST','PUT','PATCH','DELETE'].includes(String(params.method))) throw new Error('Choose a declared backend endpoint and HTTP method.')
    return (await api.request({url:`/plugins/${encodeURIComponent(plugin.manifest.id)}/backend${params.path}`,method:String(params.method),data:params.method==='GET'?undefined:params.body,params:params.query??{}})).data
  }
  if (typeof params.table !== 'string' || !plugin.manifest.tables.some(table => table.name === params.table)) throw new Error('This table is not declared by this plugin.')
  const root = `/plugins/${encodeURIComponent(plugin.manifest.id)}/data/${encodeURIComponent(params.table)}`
  if (method === 'list') return (await api.get(root, {params: {offset: params.offset ?? 0}})).data
  if (method === 'create') return (await api.post(root, {data: params.data})).data
  if (typeof params.id !== 'string' || !/^[a-f\d-]{36}$/i.test(params.id)) throw new Error('Invalid plugin record ID.')
  const url = `${root}/${encodeURIComponent(params.id)}`
  if (method === 'get') return (await api.get(url)).data
  if (method === 'update') return (await api.put(url, {data: params.data, revision: params.revision})).data
  if (method === 'remove') { await api.delete(url, {params: {revision: params.revision}}); return null }
  throw new Error('Unsupported plugin request.')
}

export function isPluginRequest(value: unknown): value is Request {
  if (!value || typeof value !== 'object') return false
  const request = value as Partial<Request>
  return typeof request.id === 'string' && request.id.length <= 80 && typeof request.method === 'string'
    && !!request.params && typeof request.params === 'object' && !Array.isArray(request.params)
}
