import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { projectsApi } from '../../api/projects'

export function ContextExtensions({kind, id}: {kind: 'CLIENT' | 'PROJECT'; id: string}) {
  const extensions = useQuery({queryKey: ['context-extensions', kind, id], queryFn: () => projectsApi.extensions(kind, id)})
  if (extensions.isError) return <p role="alert">Unable to load extensions. <button onClick={() => void extensions.refetch()}>Retry</button></p>
  if (!extensions.data?.length) return null
  return <section className="page-stack"><h2>Extensions</h2>{extensions.data.map(extension => <article className="page-stack" key={extension.pluginId}><Link to={extension.href}>{extension.label}</Link>{extension.panels.map((panel, index) => <div key={index}><h3>{panel.title}</h3><p>{panel.text}</p></div>)}</article>)}</section>
}
