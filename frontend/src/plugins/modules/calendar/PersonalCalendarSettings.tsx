import { pluginRole } from '../../../api/access'
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuthStore } from '../../../hooks/useAuthStore'
import { useAvailablePlugins } from '../../../api/plugins'
import { getErrorMessage } from '../../../components/admin/adminUtils'
import { Button } from '../../../components/ui'
import {calendarApi,type PersonalSchema,type PersonalConnection} from './api'

export function PersonalCalendarSettings({expanded=false}: {expanded?:boolean}) {
  const user=useAuthStore(state => state.user)
  const plugins=useAvailablePlugins()
  const policy=plugins.data?.find(plugin => plugin.manifest.id==='calendar')?.tableAccess
  const readable=!!user && ['plugin_calendar_connections','plugin_calendar_sync_links'].every(table => policy?.[table]?.readRoles.includes(pluginRole(user.role)))
  const client=useQueryClient()
  const query=useQuery({queryKey:['plugin-calendar-personal',user?.id,user?.role],queryFn:calendarApi.personal,enabled:readable})
  const changed=async () => {await Promise.all([client.invalidateQueries({queryKey:['plugin-calendar-personal']}),client.invalidateQueries({queryKey:['plugin-calendar-events']})])}
  const sync=useMutation({mutationFn:calendarApi.sync,onSuccess:changed})
  const disconnect=useMutation({mutationFn:() => calendarApi.disconnect(query.data!.connection!.revision),onSuccess:async () => {setConfirm(false);await changed()}})
  const pause=useMutation({mutationFn:() => calendarApi.savePersonal({providerId:query.data!.connection!.providerId,values:{},clearSecrets:[],enabled:false,revision:query.data!.connection!.revision}),onSuccess:changed})
  const [confirm,setConfirm]=useState(false)
  if (!readable) return null
  const connection=query.data?.connection
  const schemas=query.data?.providers ?? []
  return <details className="page-stack" open={expanded||undefined}><summary>Your calendar sync{connection?.enabled ? ' · Two-way sync enabled' : ''}</summary>
    <div className="page-stack">
      <p>Connect your own calendar for {user?.email}. Sync imports and updates your events, and sends your included local events and deletions back. No other member’s calendar is selected.</p>
      <p className="field-help">One personal connection per account. Automatic sync runs about every five minutes over the last 30 and next 60 days. Board deadlines stay internal. Recurring and invited meetings remain provider-managed; this first sync version handles personal appointments.</p>
      {query.isLoading && <p role="status">Loading your connection…</p>}
      {connection && <><p role="status">{connection.status}{connection.lastSyncAt && ` · Last synced ${new Date(connection.lastSyncAt).toLocaleString()}`}</p><div className="inline-actions"><Button variant="secondary" disabled={!connection.enabled || !query.data?.canWrite || sync.isPending} onClick={() => sync.mutate()}>Sync now</Button><Button variant="ghost" onClick={() => void query.refetch()}>Refresh sync status</Button>{connection.enabled && <Button variant="ghost" disabled={!query.data?.canWrite || pause.isPending} onClick={() => pause.mutate()}>Pause sync</Button>}</div></>}
      {query.data && <PersonalConnectionForm key={`${connection?.providerId ?? 'new'}:${connection?.revision ?? -1}`} connection={connection} schemas={schemas} canWrite={query.data.canWrite} onSaved={changed} />}
      {connection && query.data?.canWrite && <div className="inline-actions">{confirm ? <><span>Remove stored credentials and stop sync? Local events and provider copies are retained.</span><Button disabled={disconnect.isPending} onClick={() => disconnect.mutate()}>Confirm disconnect</Button><Button variant="ghost" onClick={() => setConfirm(false)}>Keep connection</Button></> : <Button variant="ghost" onClick={() => setConfirm(true)}>Disconnect calendar</Button>}</div>}
      {(query.isError || sync.isError || pause.isError || disconnect.isError) && <p role="alert">{getErrorMessage(query.error ?? sync.error ?? pause.error ?? disconnect.error,'Unable to update your personal calendar connection.')}</p>}
    </div>
  </details>
}
function PersonalConnectionForm({connection,schemas,canWrite,onSaved}: {connection?: PersonalConnection; schemas: PersonalSchema[]; canWrite: boolean; onSaved: () => Promise<void>}) {
  const [provider,setProvider]=useState(connection?.providerId ?? '')
  const [values,setValues]=useState<Record<string,string>>(connection?.values ?? {})
  const [cleared,setCleared]=useState<string[]>([])
  const [enabled,setEnabled]=useState(connection?.enabled ?? false)
  const schema=schemas.find(item => item.id===provider)
  const save=useMutation({mutationFn:() => calendarApi.savePersonal({providerId:provider,values,clearSecrets:cleared,enabled,revision:connection?.revision ?? -1}),onSuccess:onSaved})
  return <form className="page-stack" onSubmit={event => {event.preventDefault();save.mutate()}}>
    {!schemas.length && <p className="field-help">Your administrator must enable a calendar provider for your role before you can connect it. Existing connections can still be paused or disconnected.</p>}
    <fieldset className="page-stack" disabled={!canWrite || save.isPending}>
      <label className="form-field">Your calendar provider<select value={provider} disabled={!!connection} onChange={event => {setProvider(event.target.value);setValues({});setCleared([])}}><option value="">Choose provider</option>{connection && !schema && <option value={connection.providerId}>{connection.providerId} (unavailable)</option>}{schemas.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
      {schema && <><p className="field-help">Use your own OAuth offline grant or CalDAV app password. Credentials are encrypted and never displayed after saving. Microsoft uses your delegated /me calendar; administrator shared-calendar credentials are separate.</p>{schema.fields.map(field => {
        const stored=connection?.configuredSecrets.includes(field.key)
        return <div className="page-stack" key={field.key}><label className="form-field">{field.label}{field.required ? ' *' : ''}<input type={field.type==='SECRET' ? 'password' : 'text'} autoComplete="off" maxLength={field.maxLength} value={values[field.key] ?? ''} disabled={cleared.includes(field.key)} placeholder={stored ? 'Stored — leave blank to keep' : ''} onChange={event => setValues({...values,[field.key]:event.target.value})} /></label>{field.help && <small>{field.help}</small>}{stored && <label className="checkbox-label"><input type="checkbox" checked={cleared.includes(field.key)} onChange={event => {setCleared(event.target.checked ? [...cleared,field.key] : cleared.filter(key => key!==field.key));setValues({...values,[field.key]:''})}} />Remove stored {field.label.toLowerCase()}</label>}</div>
      })}<label className="checkbox-label"><input type="checkbox" checked={enabled} onChange={event => setEnabled(event.target.checked)} />Enable automatic two-way sync for my events</label><p className="field-help">Updates and deletions propagate both ways for included events. Conflicts preserve your local changes as a local-only copy. Pausing retains existing records.</p><Button type="submit">Save my connection</Button></>}
    </fieldset>
    {save.isError && <p role="alert">{getErrorMessage(save.error,'Unable to save your connection.')}</p>}{save.isSuccess && <p role="status">Your connection is saved.</p>}
  </form>
}
