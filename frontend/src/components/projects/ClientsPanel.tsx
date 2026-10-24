import {useState} from 'react'
import {useQuery,useQueryClient} from '@tanstack/react-query'
import {Link,useNavigate} from 'react-router-dom'
import {projectsApi,type Client,type ClientInput} from '../../api/projects'
import {ClientEditor} from './ClientEditor'
import {ClientLogoImage} from './ClientLogo'
import {Button} from '../ui'

export function ClientsPanel({canCreate=false}:{canCreate?:boolean}) {
  const client=useQueryClient(),navigate=useNavigate(),clients=useQuery({queryKey:['project-clients'],queryFn:projectsApi.clients})
  const [draft,setDraft]=useState<ClientInput>(),[editingId,setEditingId]=useState<string>()
  const edit=(value?:Client)=>{setEditingId(value?.id);setDraft(value??{name:'',details:{description:'',contacts:[],links:[]},revision:-1})}
  const saved=(value:Client)=>{
    void client.invalidateQueries({queryKey:['project-clients']});void client.invalidateQueries({queryKey:['project']});void client.invalidateQueries({queryKey:['projects']})
    navigate(`/projects/clients/${value.id}`)
  }
  return <section className="page-stack"><div className="inline-actions"><h2>Clients</h2>{canCreate&&<Button onClick={()=>edit()} disabled={!!draft}>New client</Button>}</div>
    <p>Set contacts on each client. Linked projects use those contacts unless they have a project-specific override. Project members can open the client's logo, contacts and links.</p>
    {clients.isLoading&&<p role="status">Loading clients…</p>}{clients.isError&&<p role="alert">Unable to load clients.</p>}
    {!draft&&<div className="project-table-wrap"><table className="project-table" aria-label="Clients"><thead><tr><th>Client</th><th>Contacts</th><th>Actions</th></tr></thead><tbody>{clients.data?.map(item=><tr key={item.id}>
      <td><Link className="client-identity" to={`/projects/clients/${item.id}`}><ClientLogoImage id={item.id} value={item.logo} size={32}/><span>{item.name}</span></Link></td><td>{item.details.contacts.length}</td>
      <td>{item.canManage&&<Button variant="ghost" onClick={()=>edit(item)}>Edit client</Button>}</td>
    </tr>)}</tbody></table></div>}
    {draft&&<ClientEditor id={editingId} initial={draft} onSaved={saved} onCancel={()=>setDraft(undefined)}/>}
  </section>
}
