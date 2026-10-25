import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { pluginsApi, type PluginView } from '../../../api/plugins'
import { getErrorMessage } from '../adminUtils'
import { Button } from '../../ui'
import {RepositoryScopeSettings} from './RepositoryScopeSettings'
import {TimeZoneSelect} from '../../ui/TimeZoneSelect'

export function PluginConfigurationForm({plugin,fieldKeys,title}: {plugin: PluginView;fieldKeys?:string[];title?:string}) {
  const id = plugin.manifest.id
  const git=['github','gitlab','gitea'].includes(id)
  const fields=plugin.manifest.settingsFields.filter(f=>!fieldKeys||fieldKeys.includes(f.key))
  const client = useQueryClient()
  const configuration = useQuery({queryKey:['plugin-configuration', id], queryFn: () => pluginsApi.configuration(id), refetchOnWindowFocus:false})
  const [values, setValues] = useState<Record<string, string>>({})
  const [clearSecrets, setClearSecrets] = useState<string[]>([])
  const [dirty, setDirty] = useState(false)
  useEffect(() => {if(!dirty){setValues(configuration.data?.values ?? {}); setClearSecrets([])}}, [configuration.data,dirty])
  const save = useMutation({mutationFn: async () => {
    try{return await pluginsApi.saveConfiguration(id, Object.fromEntries(Object.entries(values).filter(([key])=>fields.some(f=>f.key===key))), clearSecrets.filter(key=>fields.some(f=>f.key===key)), configuration.data?.revision ?? 0)}
    catch(failure){throw new Error(getErrorMessage(failure))} // Do not retain Axios request credentials in mutation errors.
  }, onSuccess: data => {
    client.setQueryData(['plugin-configuration', id], data)
    setValues(data.values); setClearSecrets([]); setDirty(false)
    void client.invalidateQueries({queryKey:['operations','cloud']})
  }})
  if (!fields.length) return null
  if (configuration.isLoading) return <p role="status">Loading configuration…</p>
  if (configuration.isError) return <p role="alert">Unable to load configuration. <button onClick={() => void configuration.refetch()}>Retry</button></p>
  const current = configuration.data!
  return <form className="page-stack" onSubmit={event => {event.preventDefault(); save.mutate()}}>
    <h2>{title??`${plugin.manifest.name} configuration`}</h2>
    <p className="field-help">{fields.some(f=>f.type==='SECRET')?'Credentials are encrypted on the server and never returned to the browser. Blank credential fields keep existing values. ':''}Saving applies only the settings in this section and does not contact the provider. Incomplete drafts can be saved; provider actions require the configured fields.</p>
    {!current.encryptionConfigured && <p role="alert">Configure the server encryption key before saving plugin configuration.</p>}
    {current.missingRequired.length > 0 && <p className="field-help">Required before use: {current.missingRequired.join(', ')}.</p>}
    <fieldset className="page-stack" disabled={save.isPending || !current.encryptionConfigured}>
      {fields.filter(field=>!git||!['repositoryAccess','repositories'].includes(field.key)).map(field => {
        const secret = field.type === 'SECRET'
        const stored = current.configuredSecrets.includes(field.key)
        const cleared = clearSecrets.includes(field.key)
        if (field.type === 'BOOLEAN') return <label key={field.key} className="checkbox-label"><input type="checkbox" checked={(values[field.key] ?? field.defaultValue) === 'true'} onChange={event => {setValues(previous => ({...previous,[field.key]:String(event.target.checked)}));setDirty(true);save.reset()}}/><span>{field.label}{field.help && <small className="field-help" style={{display:'block'}}>{field.help}</small>}</span></label>
        if (field.key === 'timeZone') return <TimeZoneSelect key={field.key} value={values[field.key]??field.defaultValue??'UTC'} onChange={value=>{setValues(previous=>({...previous,[field.key]:value}));setDirty(true);save.reset()}}/>
        return <div key={field.key} className="page-stack">
          <label className="form-field">{field.label}{field.required ? ' (required for use)' : ''}
            <input type={secret ? 'password' : field.key==='dailyTime'?'time':'text'} autoComplete={secret ? 'new-password' : 'off'} spellCheck={false} maxLength={field.maxLength} value={values[field.key] ?? field.defaultValue ?? ''} disabled={cleared}
              placeholder={secret && stored ? 'Stored securely' : undefined} onChange={event => {setValues(previous => ({...previous,[field.key]:event.target.value})); setDirty(true); save.reset()}} />
            {field.help && <span className="field-help">{field.help}</span>}
            {secret && stored && <span className="field-help"> A credential is stored. Leave blank to keep it, or enter a replacement.</span>}
          </label>
          {secret && stored && <label className="checkbox-label"><input type="checkbox" checked={cleared} onChange={event => {setClearSecrets(previous => event.target.checked ? [...previous,field.key] : previous.filter(key => key !== field.key)); setValues(previous => ({...previous,[field.key]:''})); setDirty(true); save.reset()}} />Remove stored {field.label.toLowerCase()}</label>}
        </div>
      })}
      {git&&<RepositoryScopeSettings id={id} values={values} revision={current.revision} ready={current.configuredSecrets.includes('accessToken')&&!values.accessToken&&!clearSecrets.includes('accessToken')&&(values.baseUrl??'')===(current.values.baseUrl??'')} onChange={(key,value)=>{setValues(previous=>({...previous,[key]:value}));setDirty(true);save.reset()}}/>}
      <div className="inline-actions"><Button type="submit" disabled={!dirty || save.isPending}>Save configuration</Button>{dirty && <Button type="button" variant="ghost" onClick={() => {setValues(current.values); setClearSecrets([]); setDirty(false); save.reset()}}>Discard changes</Button>}</div>
    </fieldset>
    {save.isError && <p className="feedback error" role="alert">{getErrorMessage(save.error)}</p>}
    {save.isSuccess && <p role="status">Configuration saved securely.</p>}
  </form>
}
