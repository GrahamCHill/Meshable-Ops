import {useState} from 'react'
import {useQuery} from '@tanstack/react-query'
import {useAuthStore} from '../../hooks/useAuthStore'
import {Field,PersonSelect,Notice,LoadState,get,post,put,useRun,isAdmin} from './shared'

interface RateValue {roleId:string;name:string;amount:string|number;currency:string;enabled:boolean}
interface Rate {id:string;revision:number;rate:RateValue}
interface Assignment {id:string;revision:number;assignment:{userId:string;roleId:string;enabled:boolean}}

function RateEditor({row}:{row:Rate}) {
  const [amount,setAmount]=useState(String(row.rate.amount)),run=useRun()
  return <details><summary>Edit hourly rate</summary><Notice state={run}/><form onSubmit={e=>{e.preventDefault();void run.run(()=>put(`billing-items/rates/${row.id}`,{rate:{...row.rate,amount},revision:row.revision}))}}><Field label="Hourly rate (excluding tax)"><input required type="number" min="0" step="0.0001" value={amount} onChange={e=>setAmount(e.target.value)}/></Field><button disabled={run.busy}>Save rate</button></form></details>
}
export function Rates() {
  const user=useAuthStore(s=>s.user),run=useRun()
  const [name,setName]=useState(''),[amount,setAmount]=useState(''),[currency,setCurrency]=useState('USD'),[roleId,setRole]=useState(''),[userId,setUser]=useState('')
  const q=useQuery({queryKey:['operations','rates'],queryFn:()=>get<{rates:Rate[];assignments:Assignment[]}>('billing-items/rates'),enabled:isAdmin(user?.role)})
  const people=useQuery({queryKey:['operations','people'],queryFn:()=>import('../../api/projects').then(m=>m.projectsApi.people()),enabled:isAdmin(user?.role)})
  if(!isAdmin(user?.role))return null
  const roles=q.data?.rates.filter((r,i,a)=>a.findIndex(x=>x.rate.roleId===r.rate.roleId)===i)??[]
  return <section><h2>Billing roles and hourly rates</h2><p>Enabled rates supply default going rates. Assign individual project rates from the project's Billing tab. Approved time retains its rate snapshot.</p><Notice state={run}/><LoadState loading={q.isLoading} error={q.isError}/>
    <div className="ops-table"><table aria-label="Billing roles and hourly rates"><thead><tr><th>Role</th><th>Hourly rate (excluding tax)</th><th>Enabled</th><th>Actions</th></tr></thead><tbody>{q.data?.rates.map(r=><tr key={r.id}><td>{r.rate.name}</td><td>{r.rate.currency} {r.rate.amount}</td><td><input type="checkbox" role="switch" aria-label={`Enable ${r.rate.name}`} checked={r.rate.enabled} disabled={run.busy} onChange={e=>void run.run(()=>put(`billing-items/rates/${r.id}`,{rate:{...r.rate,enabled:e.target.checked},revision:r.revision}))}/></td><td><RateEditor key={r.id+':'+r.revision} row={r}/></td></tr>)}</tbody></table></div>
    <form onSubmit={e=>{e.preventDefault();void run.run(()=>post('billing-items/rates',{rate:{name,amount,currency,roleId:crypto.randomUUID(),enabled:true}}),()=>{setName('');setAmount('')})}}><div className="ops-fields"><Field label="Role name"><input required maxLength={100} value={name} onChange={e=>setName(e.target.value)}/></Field><Field label="Hourly rate"><input required type="number" min="0" step="0.0001" value={amount} onChange={e=>setAmount(e.target.value)}/></Field><Field label="Currency"><input required maxLength={3} value={currency} onChange={e=>setCurrency(e.target.value.toUpperCase())}/></Field></div><button disabled={run.busy}>Add billing role</button></form>
    <h3>Default member roles</h3><form onSubmit={e=>{e.preventDefault();void run.run(()=>post('billing-items/assignments',{assignment:{userId,roleId,enabled:true}}))}}><div className="ops-fields"><PersonSelect value={userId} onChange={setUser}/><Field label="Billing role"><select required value={roleId} onChange={e=>setRole(e.target.value)}><option value="">Choose a role</option>{roles.filter(r=>r.rate.enabled).map(r=><option key={r.rate.roleId} value={r.rate.roleId}>{r.rate.name}</option>)}</select></Field></div><button disabled={!userId||!roleId||run.busy}>Assign role</button></form>
    <div className="ops-table"><table aria-label="Member billing roles"><thead><tr><th>Member</th><th>Role</th><th>Enabled</th></tr></thead><tbody>{q.data?.assignments.map(a=><tr key={a.id}><td>{people.data?.find(p=>p.id===a.assignment.userId)?.name??a.assignment.userId}</td><td>{roles.find(r=>r.rate.roleId===a.assignment.roleId)?.rate.name}</td><td><input type="checkbox" role="switch" aria-label={`Enable role for ${people.data?.find(p=>p.id===a.assignment.userId)?.name??a.assignment.userId}`} checked={a.assignment.enabled} disabled={run.busy} onChange={e=>void run.run(()=>put(`billing-items/assignments/${a.id}`,{assignment:{...a.assignment,enabled:e.target.checked},revision:a.revision}))}/></td></tr>)}</tbody></table></div>
  </section>
}
