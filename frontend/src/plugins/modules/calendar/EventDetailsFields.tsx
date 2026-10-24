import {useState} from 'react'
import {useQuery} from '@tanstack/react-query'
import {useAccess} from '../../../api/access'
import {documentsApi} from '../../../api/documents'
import {Button} from '../../../components/ui'
import type {CalendarDocumentLink,CalendarParticipant} from './api'

export function EventDetailsFields({documents,participants,onDocuments,onParticipants}: {documents:CalendarDocumentLink[];participants:CalendarParticipant[];onDocuments:(value:CalendarDocumentLink[])=>void;onParticipants:(value:CalendarParticipant[])=>void}) {
  const access=useAccess();const [chooseDocument,setChooseDocument]=useState(false)
  const generated=useQuery({queryKey:['generated-documents'],queryFn:documentsApi.list,enabled:chooseDocument&&access.can('documents')})
  return <>
    <section className="page-stack"><h3>Linked documents</h3><p className="field-help">References keep their existing access controls. Use a workspace Storage/Documents link or an HTTPS document URL.</p>
      {documents.map((document,index)=><div className="calendar-detail-row" key={index}><label className="form-field">Document label<input required maxLength={160} value={document.label} onChange={e=>onDocuments(documents.map((item,i)=>i===index?{...item,label:e.target.value}:item))}/></label><label className="form-field">Document link<input required maxLength={2048} value={document.url} placeholder="https://… or /storage?folder=…" onChange={e=>onDocuments(documents.map((item,i)=>i===index?{...item,url:e.target.value}:item))}/></label><Button type="button" variant="ghost" onClick={()=>onDocuments(documents.filter((_,i)=>i!==index))}>Remove document</Button></div>)}
      <div className="inline-actions"><Button type="button" variant="secondary" disabled={documents.length>=20} onClick={()=>onDocuments([...documents,{label:'',url:''}])}>Add document link</Button>{access.can('documents')&&<Button type="button" variant="ghost" disabled={documents.length>=20} onClick={()=>setChooseDocument(!chooseDocument)}>Choose generated document</Button>}</div>
      {chooseDocument&&<label className="form-field">Generated document<select value="" onChange={e=>{const document=generated.data?.find(item=>item.id===e.target.value);if(document){onDocuments([...documents,{label:document.name,url:`/documents?document=${document.id}`}]);setChooseDocument(false)}}}><option value="">Choose an accessible document</option>{generated.data?.map(document=><option value={document.id} key={document.id}>{document.name}</option>)}</select>{generated.isLoading&&<small>Loading documents…</small>}{generated.isError&&<small role="alert">Unable to list documents.</small>}</label>}
    </section>
    <section className="page-stack"><h3>Participants</h3><p className="field-help">Participant details are informational. Saving does not send meeting invitations or grant file access.</p>
      {participants.map((person,index)=><div className="calendar-detail-row" key={index}><label className="form-field">Participant name<input maxLength={160} value={person.name} onChange={e=>onParticipants(participants.map((item,i)=>i===index?{...item,name:e.target.value}:item))}/></label><label className="form-field">Participant email<input type="email" maxLength={254} value={person.email} onChange={e=>onParticipants(participants.map((item,i)=>i===index?{...item,email:e.target.value}:item))}/></label><Button type="button" variant="ghost" onClick={()=>onParticipants(participants.filter((_,i)=>i!==index))}>Remove participant</Button></div>)}
      <div><Button type="button" variant="secondary" disabled={participants.length>=100} onClick={()=>onParticipants([...participants,{name:'',email:'',response:''}])}>Add participant</Button></div>
    </section>
  </>
}
