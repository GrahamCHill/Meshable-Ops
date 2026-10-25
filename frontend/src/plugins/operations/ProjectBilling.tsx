import {useState} from 'react'
import {useQuery} from '@tanstack/react-query'
import {Link} from 'react-router-dom'
import {useAvailablePlugins} from '../../api/plugins'
import api from '../../api/http'
import {downloadProjectBlob} from '../../api/projects'
import {LinkedItems} from './BillingItemsPage'
import {BillingPanel} from './BillingPage'
import {BillingPaymentOptions} from './BillingPaymentOptions'
import {ProjectHours} from './TimesheetsPage'
import {DateRange,Field,Notice,LoadState,get,put,useRun,monthStart,today} from './shared'

interface MemberRate {userId:string;name:string;revision:number;rate:{amount:string|number;currency:string;enabled:boolean}|null;goingRate:{amount:string|number;currency:string}|null}
function MemberRateEditor({member,projectId}:{member:MemberRate;projectId:string}) {
  const [amount,setAmount]=useState(String(member.rate?.amount??member.goingRate?.amount??'')),[currency,setCurrency]=useState(member.rate?.currency??member.goingRate?.currency??'USD'),run=useRun()
  const enabled=member.rate?.enabled??!!member.goingRate
  const save=(enabled:boolean)=>put(`billing-items/projects/${projectId}/rates`,{rate:{userId:member.userId,amount,currency,enabled},revision:member.revision})
  return <tr><td>{member.name}</td><td>{member.goingRate?`${member.goingRate.currency} ${member.goingRate.amount}`:member.rate&&!member.rate.enabled?'Disabled':'No rate assigned'}{!member.rate&&member.goingRate&&<small> · default role rate</small>}</td><td><input type="checkbox" role="switch" aria-label={`Enable billing for ${member.name}`} checked={enabled} disabled={run.busy||amount===''} onChange={e=>void run.run(()=>save(e.target.checked))}/></td><td><Notice state={run}/><details><summary>Assign individual rate</summary><form onSubmit={e=>{e.preventDefault();void run.run(()=>save(true))}}><Field label="Hourly rate (excluding tax)"><input required type="number" min="0" step="0.0001" value={amount} onChange={e=>setAmount(e.target.value)}/></Field><Field label="Currency"><input required maxLength={3} value={currency} onChange={e=>setCurrency(e.target.value.toUpperCase())}/></Field><button disabled={run.busy}>Save project rate</button></form></details></td></tr>
}
function ProjectMemberRates({projectId}:{projectId:string}) {
  const q=useQuery({queryKey:['operations','project-member-rates',projectId],queryFn:()=>get<MemberRate[]>(`billing-items/projects/${projectId}/rates`)})
  return <section><h2>Individuals and going rates</h2><p>Choose an hourly rate for each project member. Approved timesheets retain the rate agreed when they were approved. Rate changes apply to work approved afterwards.</p><LoadState loading={q.isLoading} error={q.isError}/><div className="ops-table"><table aria-label="Project member billing rates"><thead><tr><th>Member</th><th>Going hourly rate (excluding tax)</th><th>Enabled</th><th>Actions</th></tr></thead><tbody>{q.data?.map(m=><MemberRateEditor key={m.userId+':'+m.revision+':'+m.goingRate?.amount} member={m} projectId={projectId}/>)}</tbody></table></div></section>
}
export function ProjectTimesheetExport({projectId}:{projectId:string}) {
  const [from,setFrom]=useState(monthStart),[to,setTo]=useState(today),run=useRun()
  return <section><h2>Client timesheet export</h2><p>Export approved billable project time, member names, work descriptions, hours and the approved rate amounts as CSV. Select a period of up to 93 days.</p><DateRange {...{from,to,setFrom,setTo}}/><Notice state={run}/><button disabled={run.busy} onClick={()=>void run.run(async()=>{const response=await api.get<Blob>('/plugins/timesheets/export',{params:{projectId,from,to},responseType:'blob'});downloadProjectBlob(response.data,`project-timesheets-${from}-${to}.csv`)})}>Export project timesheets</button></section>
}
export default function ProjectBilling({projectId}:{projectId:string}) {
  const [tab,setTab]=useState('assignments'),plugins=useAvailablePlugins()
  const available=(id:string)=>plugins.data?.some(p=>p.manifest.id===id)
  return <div className="ops-page"><nav className="workspace-tabs" aria-label="Project billing"><button aria-current={tab==='assignments'?'page':undefined} onClick={()=>setTab('assignments')}>Items & individuals</button><button aria-current={tab==='documents'?'page':undefined} onClick={()=>setTab('documents')}>Invoices & quotes</button><button aria-current={tab==='payments'?'page':undefined} onClick={()=>setTab('payments')}>Payment providers</button></nav>
    {tab==='assignments'&&<>{available('billing-items')?<><LinkedItems projectId={projectId}/><ProjectMemberRates projectId={projectId}/></>:<p>Enable Billing items & rates to assign project items and individual rates.</p>}{available('timesheets')&&<><ProjectHours projectId={projectId}/><p><Link to={`/plugins/timesheets?project=${projectId}&team=true`}>Review and approve reported project timesheets</Link>. Calculate charges from approved hours in Invoices & quotes, then review the PDF and sign off before sending.</p><ProjectTimesheetExport projectId={projectId}/></>}</>}
    {tab==='documents'&&<BillingPanel key={projectId} projectId={projectId}/>}{tab==='payments'&&<BillingPaymentOptions/>}
  </div>
}
