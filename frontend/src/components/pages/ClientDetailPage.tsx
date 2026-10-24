import {useState} from 'react'
import {Link,useParams} from 'react-router-dom'
import {useQuery,useQueryClient} from '@tanstack/react-query'
import {projectsApi} from '../../api/projects'
import {ClientEditor} from '../projects/ClientEditor'
import {ClientLogo} from '../projects/ClientLogo'
import {Contacts} from '../projects/ContactFields'
import {ContextExtensions} from '../projects/ContextExtensions'
import {Button} from '../ui'
import '../../styles/projects.css'

export default function ClientDetailPage() {
  const {clientId=''}=useParams(),client=useQueryClient(),[editing,setEditing]=useState(false)
  const query=useQuery({queryKey:['project-client',clientId],queryFn:()=>projectsApi.client(clientId)})
  if(query.isLoading)return <p role="status">Loading client…</p>
  if(query.isError||!query.data)return <div className="page-stack"><p role="alert">This client is unavailable.</p><Link to="/projects">Back to projects</Link></div>
  const value=query.data
  return <div className="page-stack project-details"><Link to="/projects">Back to projects</Link><div className="page-heading"><h1>{value.name}</h1>{value.canManage&&<Button disabled={editing} onClick={()=>setEditing(true)}>Edit client details</Button>}</div>
    <ClientLogo id={clientId} canManage={value.canManage}/>
    {editing?<ClientEditor id={clientId} initial={value} onCancel={()=>setEditing(false)} onSaved={saved=>{client.setQueryData(['project-client',clientId],saved);void client.invalidateQueries({queryKey:['project-clients']});void client.invalidateQueries({queryKey:['project']});void client.invalidateQueries({queryKey:['projects']});setEditing(false)}}/>:<>
      <p className="project-note">{value.details.description}</p><h2>Client contacts</h2><Contacts contacts={value.details.contacts} roleLabel="Role at client"/>
      <h2>Web links</h2>{value.details.links.map((link,index)=><a key={index} href={link.url} target="_blank" rel="noreferrer">{link.label}</a>)}
      <Link to={`/projects?client=${clientId}`}>View client projects</Link><ContextExtensions kind="CLIENT" id={clientId}/>
    </>}
  </div>
}
