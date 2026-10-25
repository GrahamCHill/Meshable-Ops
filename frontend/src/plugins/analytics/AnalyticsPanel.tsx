import {useState} from 'react'
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query'
import {ExternalLink,RefreshCw,Link2} from 'lucide-react'
import type {PluginView} from '../../api/plugins'
import {Button} from '../../components/ui'
import {getErrorMessage} from '../../components/admin/adminUtils'
import {useAuthStore} from '../../hooks/useAuthStore'
import {analyticsApi,type AnalyticsContext,type AnalyticsFilter,type AnalyticsLink,type AnalyticsMetric} from './api'
import '../project-plugins.css'
import './analytics.css'
import {AnalyticsExports} from './AnalyticsExports'

function day(offset:number) {const date=new Date();date.setUTCDate(date.getUTCDate()+offset);return date.toISOString().slice(0,10)}
function format(metric:AnalyticsMetric,currency:string|null) {
  if(metric.value===null)return 'Unavailable'
  if(metric.unit==='BYTES') {const power=metric.value>0?Math.min(4,Math.floor(Math.log10(metric.value)/3)):0;return `${new Intl.NumberFormat(undefined,{maximumFractionDigits:1}).format(metric.value/1000**power)} ${['B','KB','MB','GB','TB'][power]}`}
  if(metric.unit==='CURRENCY'&&currency)return new Intl.NumberFormat(undefined,{style:'currency',currency,maximumFractionDigits:2}).format(metric.value)
  return new Intl.NumberFormat(undefined,{maximumFractionDigits:2}).format(metric.value)+(metric.unit==='PERCENT'?'%':metric.unit==='CURRENCY'?' (currency unavailable)':'')
}
const filters:Record<AnalyticsFilter,string>={ALL:'Entire resource',CAMPAIGN:'Specific campaign',PAGE:'Specific page path'}

export function AnalyticsPanel({plugin,kind,contextId,canManage}:{plugin:PluginView;kind:AnalyticsContext;contextId:string;canManage:boolean}) {
  const user=useAuthStore(state=>state.user),client=useQueryClient(),id=plugin.manifest.id,api=analyticsApi(id,kind,contextId)
  const queryKey=['analytics-links',user?.id,id,kind,contextId]
  const links=useQuery({queryKey,queryFn:api.list,retry:false}),options=useQuery({queryKey:['analytics-options',user?.id,id,kind,contextId],queryFn:api.options,retry:false})
  const [label,setLabel]=useState(''),[resource,setResource]=useState(''),[filter,setFilter]=useState<AnalyticsFilter>('ALL'),[selector,setSelector]=useState('')
  const [from,setFrom]=useState(()=>day(-28)),[to,setTo]=useState(()=>day(-1)),[notice,setNotice]=useState('')
  const validDates=!!from&&!!to&&from<=to&&to<day(0)&&(Date.parse(to)-Date.parse(from))/86400000<=30&&(id!=='cloudflare-analytics'||from>=day(-30))
  const add=useMutation({mutationFn:()=>api.link(label.trim(),{resourceId:resource,filter,selector:filter==='ALL'?'':selector.trim()}),onSuccess:()=>{setLabel('');setSelector('');setNotice('Metrics linked. Refresh to load the first snapshot.');void client.invalidateQueries({queryKey})}})
  const update=useMutation({mutationFn:({action,link}:{action:'refresh'|'remove';link:AnalyticsLink})=>action==='refresh'?api.sync(link,from,to):api.remove(link),onSuccess:(_result,{action})=>{setNotice(action==='refresh'?'Metric snapshot refreshed.':'Metrics unlinked.');void client.invalidateQueries({queryKey})},onError:()=>void client.invalidateQueries({queryKey})})
  const writable=canManage&&!update.isPending&&!add.isPending
  return <section className="page-stack project-plugin-panel analytics-panel" aria-label={plugin.manifest.name+' metrics'}>
    <p className="field-help">{kind==='CLIENT'?'Client metrics are shared with people who can view this client. Project-only links stay on their own projects.':'These links and snapshots are visible to members with access to this project and connector.'} Refreshes read reporting data from the provider.</p>
    <div className="analytics-export-heading"><div><strong>Export saved insights</strong><small>Download all saved snapshots in this connector, or export an individual link below. Each snapshot retains its own dates and source notes.</small></div><AnalyticsExports plugin={id} kind={kind} contextId={contextId} disabled={!links.data?.some(link=>link.snapshot)}/></div>
    {canManage&&<div className="analytics-period"><div><strong>Next refresh period</strong><small>Choose up to 31 completed days. Each saved snapshot keeps its own dates.</small></div><label className="form-field">From<input type="date" min={id==='cloudflare-analytics'?day(-30):'2000-01-01'} max={to||day(-1)} value={from} onChange={event=>setFrom(event.target.value)}/></label><label className="form-field">To<input type="date" min={from} max={day(-1)} value={to} onChange={event=>setTo(event.target.value)}/></label>{!validDates&&<p role="alert">Choose a valid period of up to 31 days ending yesterday or earlier. Cloudflare starts must be within the last 30 days.</p>}</div>}
    {notice&&<p role="status">{notice}</p>}
    {links.isLoading&&<p role="status">Loading linked metrics…</p>}{links.isError&&<p role="alert">{getErrorMessage(links.error)} <button onClick={()=>void links.refetch()}>Retry</button></p>}
    {links.data?.map(link=><article className="project-plugin-card page-stack clickable-card" key={link.id}><div className="page-heading"><div><h3>{link.label}</h3><p className="analytics-source">{options.data?.resourceLabel??'Resource'}: {link.selection.resourceId}<span>{filters[link.selection.filter]}{link.selection.selector?`: ${link.selection.selector}`:''}</span></p><a className="card-primary-link" href={link.url} target="_blank" rel="noopener noreferrer">Open {plugin.manifest.name} <ExternalLink size={13}/></a></div>{canManage&&<div className="inline-actions"><Button size="sm" variant="secondary" disabled={!writable||!validDates||!options.data?.configured} onClick={()=>update.mutate({action:'refresh',link})}><RefreshCw size={14}/>Refresh metrics</Button><Button size="sm" variant="ghost" disabled={!writable} onClick={()=>update.mutate({action:'remove',link})}>Unlink</Button></div>}</div>
      {link.snapshot?<><div className="analytics-snapshot-heading"><strong>{link.snapshot.from} – {link.snapshot.to}</strong><span>Refreshed {new Date(link.snapshot.syncedAt).toLocaleString()}{Date.now()-Date.parse(link.snapshot.syncedAt)>86400000?' · More than 24 hours old':''}</span></div><dl className="analytics-metrics">{link.snapshot.metrics.map(metric=><div key={metric.key}><dt>{metric.label}</dt><dd>{format(metric,link.snapshot!.currency)}</dd></div>)}</dl><AnalyticsExports plugin={id} kind={kind} contextId={contextId} linkId={link.id}/><details className="analytics-notes"><summary>Source and reporting notes</summary>{link.snapshot.notes.map((note,index)=><p key={index}>{note}</p>)}<p>Metrics from different providers, overlapping links or different currencies are not combined.</p></details></>:<p className="field-help">No snapshot yet. {canManage?'Refresh metrics to load this resource for the selected period.':'A client owner or project manager can refresh these metrics.'}</p>}
    </article>)}
    {links.data?.length===0&&<p>No metrics are linked to this {kind.toLowerCase()} yet.</p>}
    {update.isError&&<p role="alert">{getErrorMessage(update.error)} The last saved snapshot is retained if a refresh fails.</p>}
    {options.isError&&<p role="alert">Unable to load connector options. <button onClick={()=>void options.refetch()}>Retry</button></p>}
    {options.data&&!options.data.configured&&<p role="status">An administrator needs to finish the {plugin.manifest.name} credentials and approved resource settings. Existing snapshots can still be viewed for approved resources.</p>}
    {canManage&&options.data?.configured&&options.data.resources.length===0&&<p role="status">No resources are assigned to this {kind.toLowerCase()}. Ask an administrator to approve and assign the appropriate account, Page, property or zone in this connector's settings.</p>}
    {canManage&&options.data?.configured&&<form className="project-plugin-add analytics-link-form" onSubmit={event=>{event.preventDefault();add.mutate()}}><label className="form-field">Link name<input required maxLength={120} value={label} placeholder="Autumn launch campaign" onChange={event=>setLabel(event.target.value)}/></label><label className="form-field">{options.data.resourceLabel}<select required value={resource} onChange={event=>setResource(event.target.value)}><option value="">Choose an approved resource</option>{options.data.resources.map(value=><option key={value}>{value}</option>)}</select></label><label className="form-field">Metrics scope<select value={filter} onChange={event=>{setFilter(event.target.value as AnalyticsFilter);setSelector('')}}>{options.data.filters.map(value=><option key={value} value={value}>{filters[value]}</option>)}</select></label>{filter!=='ALL'&&<label className="form-field">{filter==='PAGE'?'Exact page path':id==='google-analytics'?'Exact session campaign name':'Campaign ID'}<input required maxLength={500} value={selector} placeholder={filter==='PAGE'?'/launch':id==='google-analytics'?'autumn-launch':'123456789'} onChange={event=>setSelector(event.target.value)}/><small>{filter==='PAGE'?'Path only; omit query parameters and fragments.':id==='google-analytics'?'Case-sensitive GA4 session campaign name.':'Only metrics from this campaign in the selected account are requested.'}</small></label>}<Button type="submit" loading={add.isPending} disabled={!writable||!label.trim()||!resource||filter!=='ALL'&&!selector.trim()}><Link2 size={14}/>Link metrics</Button>{add.isError&&<p role="alert">{getErrorMessage(add.error)}</p>}</form>}
  </section>
}
