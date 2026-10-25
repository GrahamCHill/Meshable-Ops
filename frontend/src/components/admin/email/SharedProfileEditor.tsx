import {useEffect,useState} from 'react'
import {sharedSettingsApi,type SharedProfile,type SharedProfileInput,type SharedSchema} from '../../../api/sharedSettings'
import {getErrorMessage} from '../adminUtils'
import {Button} from '../../ui'

const draftFor=(profile?:SharedProfile):SharedProfileInput=>({name:profile?.name??'',enabled:profile?.enabled??false,values:profile?.values??{},clearSecrets:[],revision:profile?.revision??-1})
export function SharedProfileEditor({schema,profile,email,onSaved}:{schema:SharedSchema;profile?:SharedProfile;email:boolean;onSaved:(p:SharedProfile)=>void}) {
  const [draft,setDraft]=useState(()=>draftFor(profile)),[dirty,setDirty]=useState(false),[saving,setSaving]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState('')
  useEffect(()=>{if(!dirty)setDraft(draftFor(profile))},[profile,dirty])
  const change=(patch:Partial<SharedProfileInput>)=>{setDraft(v=>({...v,...patch}));setDirty(true);setError('');setNotice('')}
  const save=async()=>{
    setSaving(true);setError('');setNotice('')
    try{const result=await sharedSettingsApi(email).save(schema.schema.id,profile?.id,draft);setDraft(draftFor(result));setDirty(false);onSaved(result);setNotice('Connection saved securely.')}
    catch(failure){setError(getErrorMessage(failure))}finally{setSaving(false)}
  }
  return <form className="page-stack" onSubmit={e=>{e.preventDefault();void save()}}>
    <h2>{profile?'Edit connection':'New connection'}</h2>
    <p className="field-help">Credentials are encrypted on the server and never returned here. Leave a password blank to keep it. Changing the server or account identity requires a replacement password. Saving does not connect to the provider.</p>
    {!schema.encryptionConfigured&&<p role="alert">Configure APP_SECRETS_ENCRYPTION_KEY on the server before saving connections.</p>}
    <fieldset className="page-stack" disabled={saving||!schema.encryptionConfigured}>
      <label className="form-field">Connection name<input value={draft.name} maxLength={100} required onChange={e=>change({name:e.target.value})}/></label>
      <label className="checkbox-label"><input type="checkbox" checked={draft.enabled} onChange={e=>change({enabled:e.target.checked})}/>Enable this connection for linked plugins</label>
      {schema.schema.fields.map(field=>{
        const secret=field.type==='SECRET',stored=profile?.configuredSecrets.includes(field.key),cleared=draft.clearSecrets.includes(field.key)
        if(field.type==='BOOLEAN')return <label key={field.key} className="checkbox-label"><input type="checkbox" checked={(draft.values[field.key]??field.defaultValue)==='true'} onChange={e=>change({values:{...draft.values,[field.key]:String(e.target.checked)}})}/>{field.label}{field.help&&<small>{field.help}</small>}</label>
        return <div key={field.key} className="page-stack"><label className="form-field">{field.label}{field.required?' (required when enabled)':''}<input type={secret?'password':'text'} autoComplete={secret?'new-password':'off'} spellCheck={false} value={draft.values[field.key]??field.defaultValue??''} disabled={cleared} maxLength={field.maxLength} placeholder={secret&&stored?'Stored securely':undefined} onChange={e=>change({values:{...draft.values,[field.key]:e.target.value}})}/>{field.help&&<small>{field.help}</small>}{secret&&stored&&<small>A credential is stored. Leave blank to keep it, or enter a replacement.</small>}</label>{secret&&stored&&<label className="checkbox-label"><input type="checkbox" checked={cleared} onChange={e=>change({clearSecrets:e.target.checked?[...draft.clearSecrets,field.key]:draft.clearSecrets.filter(k=>k!==field.key),values:{...draft.values,[field.key]:''}})}/>Remove stored {field.label.toLowerCase()}</label>}</div>
      })}
      <p className="field-help">Disable a connection to pause its use without removing plugin links. Complete all required fields before enabling it.</p>
      <div className="inline-actions"><Button type="submit" disabled={!dirty}>Save connection</Button>{dirty&&<Button type="button" variant="ghost" onClick={()=>{setDraft(draftFor(profile));setDirty(false);setError('');setNotice('')}}>Discard changes</Button>}</div>
    </fieldset>
    {error&&<p role="alert" className="feedback error">{error}</p>}{notice&&<p role="status">{notice}</p>}
  </form>
}
