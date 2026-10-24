import {useState} from 'react'
import {projectsApi,type Client,type ClientInput} from '../../api/projects'
import {ContactFields,LinkFields} from './ContactFields'
import {getErrorMessage} from '../admin/adminUtils'
import {Button} from '../ui'

export function ClientEditor({id,initial,onSaved,onCancel}:{id?:string;initial:ClientInput;onSaved:(client:Client)=>void;onCancel:()=>void}) {
  const [draft,setDraft]=useState(initial),[busy,setBusy]=useState(false),[error,setError]=useState('')
  const save=async()=>{setBusy(true);setError('');try{onSaved(await projectsApi.saveClient(id,draft))}catch(failure){setError(getErrorMessage(failure))}finally{setBusy(false)}}
  return <form className="page-stack" onSubmit={event=>{event.preventDefault();void save()}}><fieldset disabled={busy} className="page-stack">
    <label className="form-field">Client name<input required maxLength={160} value={draft.name} onChange={event=>setDraft({...draft,name:event.target.value})}/></label>
    <label className="form-field">Description<textarea maxLength={12000} rows={4} value={draft.details.description} onChange={event=>setDraft({...draft,details:{...draft.details,description:event.target.value}})}/></label>
    <h3>Client contacts</h3><p className="field-help">Record each person's job title and role at this client, such as billing contact or decision maker. Contact roles do not grant application permissions.</p>
    <ContactFields value={draft.details.contacts} roleLabel="Role at client" onChange={contacts=>setDraft({...draft,details:{...draft.details,contacts}})}/>
    <h3>Web links</h3><LinkFields value={draft.details.links} onChange={links=>setDraft({...draft,details:{...draft.details,links}})}/>
    <div className="inline-actions"><Button type="submit" loading={busy}>Save client</Button><Button type="button" variant="ghost" onClick={onCancel}>Cancel</Button></div>
  </fieldset>{error&&<p role="alert">{error}</p>}</form>
}
