import type {Person} from '../../../api/projects'
import {useQuery} from '@tanstack/react-query'
import api from '../../../api/http'
import type {TicketAccessPolicy} from './api'
export const defaultTicketAccess: TicketAccessPolicy = {createMode:'ANY_MEMBER', creatorIds:[], deleteMode:'ALL_ADMINS', deleterIds:[]}
export function TicketAccessSettings({value=defaultTicketAccess, people, onChange}:{value?:TicketAccessPolicy;people:Person[];onChange:(p:TicketAccessPolicy)=>void}) {
  const admins = useQuery({queryKey:['ticket-permission-admins'],queryFn:()=>api.get<{id:string;name:string;role:string;active:boolean}[]>('/admin/users').then(r=>r.data)})
  const select=(field:'creatorIds'|'deleterIds',id:string,checked:boolean)=>onChange({...value,[field]:checked?[...value[field],id]:value[field].filter(i=>i!==id)})
  return <section className="page-stack"><h2>Ticket creation and deletion</h2><p>These permissions apply within this board's existing access controls. Only administrators can delete tickets or change these settings.</p>
    <label className="form-field">Who can create tickets<select value={value.createMode} onChange={e=>onChange({...value,createMode:e.target.value as TicketAccessPolicy['createMode']})}><option value="ANY_MEMBER">Any board member with write access</option><option value="SELECTED_MEMBERS">Selected board members</option><option value="ADMINS">Administrators only</option></select></label>
    {value.createMode==='SELECTED_MEMBERS'&&<div className="page-stack">{people.map(p=><label className="checkbox-label" key={p.id}><input type="checkbox" checked={value.creatorIds.includes(p.id)} onChange={e=>select('creatorIds',p.id,e.target.checked)}/>{p.name}</label>)}</div>}
    <label className="form-field">Who can delete tickets<select value={value.deleteMode} onChange={e=>onChange({...value,deleteMode:e.target.value as TicketAccessPolicy['deleteMode']})}><option value="ALL_ADMINS">All administrators with board write access</option><option value="SELECTED_ADMINS">Selected administrators</option></select></label>
    {value.deleteMode==='SELECTED_ADMINS'&&<>{admins.isError&&<p role="alert">Unable to load administrators.</p>}{admins.data?.filter(p=>p.active&&['ADMIN','SUPER_ADMIN'].includes(p.role)).map(p=><label key={p.id} className="checkbox-label"><input type="checkbox" checked={value.deleterIds.includes(p.id)} onChange={e=>select('deleterIds',p.id,e.target.checked)}/>{p.name}</label>)}</>}
  </section>
}
