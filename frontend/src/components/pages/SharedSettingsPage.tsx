import {useState} from 'react'
import {useQuery,useQueryClient} from '@tanstack/react-query'
import {Link,useParams} from 'react-router-dom'
import {sharedSettingsApi,type SharedProfile} from '../../api/sharedSettings'
import {AdminPageHeader} from '../admin/AdminPageHeader'
import {SharedProfileEditor} from '../admin/email/SharedProfileEditor'
import {Button} from '../ui'

export default function SharedSettingsPage({incomingEmail=false}:{incomingEmail?:boolean}) {
  const params=useParams(),schemaId=incomingEmail?'communications.imaps':params.schemaId??'',client=useQueryClient(),[selected,setSelected]=useState('')
  const api=sharedSettingsApi(incomingEmail)
  const schema=useQuery({queryKey:['shared-schema',schemaId],queryFn:()=>api.schema(schemaId),refetchInterval:15000})
  const profiles=useQuery({queryKey:['shared-profiles',schemaId],queryFn:()=>api.profiles(schemaId),enabled:!!schema.data&&!schema.isError})
  const saved=(profile:SharedProfile)=>{client.setQueryData<SharedProfile[]>(['shared-profiles',schemaId],previous=>[...(previous??[]).filter(p=>p.id!==profile.id),profile].sort((a,b)=>a.name.localeCompare(b.name)));setSelected(profile.id);void client.invalidateQueries({queryKey:['plugin-shared-settings']})}
  if(schema.isLoading)return <p role="status">Loading shared connections…</p>
  if(schema.isError||!schema.data||(!incomingEmail&&schema.data.schema.adminMenu.category!==params.sectionId))return <div className="page-stack"><AdminPageHeader title="Connection settings unavailable" description="The provider must be enabled and you need permission to manage its settings."/><button onClick={()=>void schema.refetch()}>Retry</button></div>
  return <div className="page-stack" style={{maxWidth:900}}>
    <AdminPageHeader title={schema.data.schema.label} description={schema.data.schema.description}/>
    {incomingEmail&&<><p><strong>SMTP / email API: outgoing.</strong> Sends invoices, notifications, and attachments. Configure the shared sender in <Link to="/admin/email">Outgoing email</Link>. SMTP commonly uses port 587 with STARTTLS or 465 with TLS; API providers use HTTPS.</p><p><strong>IMAPS: incoming.</strong> Reads replies from a mailbox over TLS, usually on port 993. Use your provider’s incoming server and mailbox login/app password. These may differ from SMTP details. IMAPS does not send mail, and sending an invoice does not require an inbox connection.</p><p>Set the outgoing Reply-to address to a mailbox that delivers into the selected inbox. Then choose this connection in a plugin’s reply-processing settings. Several plugins can share a connection; each controls its own processing.</p></>}
    {profiles.isLoading&&<p role="status">Loading connections…</p>}
    {profiles.isError&&<p role="alert">Unable to load connections. <button onClick={()=>void profiles.refetch()}>Retry</button></p>}
    {profiles.data&&<>
      <div className="inline-actions"><label className="form-field">Saved connections<select value={selected} onChange={e=>setSelected(e.target.value)}><option value="">New connection</option>{profiles.data.map(p=><option key={p.id} value={p.id}>{p.name}{!p.enabled?' — disabled':''}</option>)}</select></label><Button variant="ghost" onClick={()=>setSelected('')}>New connection</Button></div>
      <SharedProfileEditor key={`${schemaId}:${selected}`} schema={schema.data} profile={profiles.data.find(p=>p.id===selected)} email={incomingEmail} onSaved={saved}/>
    </>}
  </div>
}
