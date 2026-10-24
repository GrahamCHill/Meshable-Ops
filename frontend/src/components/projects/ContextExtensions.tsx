import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { projectsApi } from '../../api/projects'
import {lazy,Suspense,useMemo} from 'react'
import {useAvailablePlugins,type PluginView} from '../../api/plugins'
import {extensionModule} from '../../plugins/registry'

function ContextPluginPanel({plugin,kind,id}: {plugin:PluginView;kind:string;id:string}) {
  const loader=extensionModule(plugin,kind.toLowerCase()+'.overview'),Panel=useMemo(()=>loader?lazy(loader):undefined,[loader])
  return Panel?<Suspense fallback={<p role="status">Loading extension…</p>}><Panel plugin={plugin} slot={kind.toLowerCase()+'.overview'} context={{kind,id}}/></Suspense>:null
}

export function ContextExtensions({kind, id}: {kind: 'CLIENT' | 'PROJECT'; id: string}) {
  const extensions = useQuery({queryKey: ['context-extensions', kind, id], queryFn: () => projectsApi.extensions(kind, id)})
  const plugins=useAvailablePlugins()
  if (extensions.isError) return <p role="alert">Unable to load extensions. <button onClick={() => void extensions.refetch()}>Retry</button></p>
  if (!extensions.data?.length) return null
  return <section className="page-stack"><h2>Extensions</h2>{extensions.data.map(extension => {const plugin=plugins.data?.find(item=>item.manifest.id===extension.pluginId);return <article className="page-stack" key={extension.pluginId}><Link to={extension.href}>{extension.label}</Link>{extension.panels.map((panel, index) => <div key={index}><h3>{panel.title}</h3><p>{panel.text}</p></div>)}{plugin&&<ContextPluginPanel plugin={plugin} kind={kind} id={id}/>}</article>})}</section>
}
