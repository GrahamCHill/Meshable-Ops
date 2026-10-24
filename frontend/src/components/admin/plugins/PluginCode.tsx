import {useQuery} from '@tanstack/react-query'
import {pluginsApi} from '../../../api/plugins'

export function PluginCode({id}: {id:string}) {
  const code=useQuery({queryKey:['plugin-code',id],queryFn:()=>pluginsApi.code(id)})
  if(code.isLoading)return <p role="status">Loading package code…</p>
  if(code.isError)return <p role="alert">Unable to inspect uploaded package code.</p>
  const backend=code.data?.backend
  if(!backend)return <p>This package provides frontend functionality.</p>
  return <section className="page-stack"><h2>Uploaded backend code</h2><p>These modules run in disposable workers. Host access follows the endpoint permissions below, the signed-in member’s permissions, and table ownership.</p>
    {!code.data?.runnerConfigured&&<p role="alert">Configure the plugin runner and its shared token before calling these backend endpoints.</p>}
    <ul>{Object.entries(backend.modules).map(([name,module])=><li key={name}>{name}: {module.runtime} · {module.entrypoint}</li>)}</ul>
    <div className="table-wrap"><table><thead><tr><th>Endpoint</th><th>Module</th><th>Host permissions</th></tr></thead><tbody>{backend.routes.map(route=><tr key={`${route.path}-${route.methods.join()}`}><td>{route.methods.join(', ')} /api/plugins/{id}/backend{route.path}</td><td>{route.module}</td><td>{route.permissions.join(', ')||'Computation only'}</td></tr>)}</tbody></table></div>
    <p>Allowed HTTP origins: {backend.allowedOrigins.join(', ')||'None'}</p>
  </section>
}
