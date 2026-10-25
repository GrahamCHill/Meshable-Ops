import {useState} from 'react'
import {useQuery,useQueryClient} from '@tanstack/react-query'
import {Link} from 'react-router-dom'
import {useAuthStore} from '../../hooks/useAuthStore'
import {pluginsApi} from '../../api/plugins'
import {Field,Notice,LoadState,get,put,useRun,isAdmin} from './shared'

export interface PaymentOptions {providers:{id:string;label:string;enabled:boolean;configured:boolean;payment:{label:string;instructions:string;paymentUrl:string}|null}[];defaultProviderId:string;revision:number}
export function PaymentProviderSelect({value,onChange}:{value:string;onChange:(value:string)=>void}) {
  const q=useQuery({queryKey:['operations','payment-providers'],queryFn:()=>get<PaymentOptions>('billing/payment-providers')})
  return <Field label="Payment method"><select value={value} onChange={e=>onChange(e.target.value)}><option value="">Business default{q.data?.providers.find(p=>p.id===q.data.defaultProviderId)?' · '+q.data.providers.find(p=>p.id===q.data.defaultProviderId)?.label:''}</option><option value="NONE">No payment details</option>{q.data?.providers.filter(p=>p.enabled&&p.configured).map(p=><option key={p.id} value={p.id}>{p.label}</option>)}</select>{q.isError&&<small role="alert">Payment providers could not load.</small>}</Field>
}
export function BillingPaymentOptions() {
  const user=useAuthStore(s=>s.user),run=useRun(),[selected,setSelected]=useState<string>()
  const q=useQuery({queryKey:['operations','payment-providers'],queryFn:()=>get<PaymentOptions>('billing/payment-providers')})
  const admin=isAdmin(user?.role)
  const qc=useQueryClient(),installed=useQuery({queryKey:['admin-plugins'],queryFn:pluginsApi.installed,enabled:admin})
  const toggle=(id:string)=>run.run(async()=>{const plugin=installed.data?.find(p=>p.manifest.id===id);if(!plugin)return;await pluginsApi.activation([{id,enabled:!plugin.enabled,expectedRevision:plugin.settingsRevision}]);await qc.invalidateQueries({queryKey:['admin-plugins']});await qc.invalidateQueries({queryKey:['plugins']})})
  return <section><h2>Payment provider options</h2><p>Enable and configure a payment provider plugin, then choose the default method for new invoices. Each generated PDF keeps a snapshot of its payment instructions.</p><Notice state={run}/><LoadState loading={q.isLoading} error={q.isError}/>
    {q.data&&<><Field label="Default payment method"><select disabled={!admin} value={selected??q.data.defaultProviderId} onChange={e=>setSelected(e.target.value)}><option value="">No default payment details</option>{q.data.providers.filter(p=>p.enabled&&p.configured||p.id===q.data.defaultProviderId).map(p=><option key={p.id} value={p.id}>{p.label}{!p.enabled||!p.configured?' (unavailable)':''}</option>)}</select></Field>{admin&&<button disabled={run.busy} onClick={()=>void run.run(()=>put('billing/payment-providers/default',{providerId:selected??q.data.defaultProviderId,revision:q.data.revision}),()=>setSelected(undefined))}>Save default payment method</button>}
    <div className="ops-table"><table aria-label="Payment providers"><thead><tr><th>Provider</th><th>Status</th><th>Payment details</th><th>Settings</th></tr></thead><tbody>{q.data.providers.map(p=><tr key={p.id}><td>{p.label}</td><td>{p.enabled?(p.configured?'Ready':'Needs configuration'):'Disabled or unavailable'}</td><td><p className="ops-note">{p.payment?.instructions}</p>{p.payment?.paymentUrl&&<a href={p.payment.paymentUrl} target="_blank" rel="noreferrer">Open hosted payment page</a>}</td><td>{admin&&<div className="ops-actions"><button disabled={run.busy||!installed.data} onClick={()=>void toggle(p.id)}>{installed.data?.find(plugin=>plugin.manifest.id===p.id)?.enabled?'Disable':'Enable'} provider plugin</button>{p.enabled&&<Link to={`/admin/sections/operations/plugins/${p.id}`}>Configure {p.label}</Link>}</div>}</td></tr>)}</tbody></table></div></>}
    <p>Payfast and other hosted providers use payment links supplied by your provider account. Payment status is recorded manually after confirming receipt.</p>
  </section>
}
