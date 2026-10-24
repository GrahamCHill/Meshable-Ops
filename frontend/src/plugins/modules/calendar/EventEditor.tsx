import { useEffect, useRef, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Button } from '../../../components/ui'
import { getErrorMessage } from '../../../components/admin/adminUtils'
import { calendarApi, type CalendarEvent, type EventInput } from './api'

function localInput(value: string) {
  const date = new Date(value)
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0,16)
}
export function EventEditor({event,onDone,canWrite,providers}: {event?: CalendarEvent; onDone: () => void; canWrite: boolean; providers: {id: string; name: string}[]}) {
  const section = useRef<HTMLElement>(null)
  useEffect(() => {section.current?.scrollIntoView({block:'start'})},[])
  const [title,setTitle] = useState(event?.title ?? '')
  const [description,setDescription] = useState(event?.description ?? '')
  const [allDay,setAllDay] = useState(event?.allDay ?? false)
  const [syncEnabled,setSyncEnabled] = useState(event?.syncEnabled ?? true)
  const [start,setStart] = useState(event ? event.allDay ? event.start : localInput(event.start) : localInput(new Date().toISOString()))
  const [end,setEnd] = useState(event ? event.allDay ? event.end : localInput(event.end) : localInput(new Date(Date.now()+3600000).toISOString()))
  const [provider,setProvider] = useState('')
  const [removeConfirm,setRemoveConfirm] = useState(false)
  const save = useMutation({mutationFn:() => {
    const input: EventInput = {title,description,start:allDay ? `${start}T00:00:00Z` : new Date(start).toISOString(),end:allDay ? `${end}T00:00:00Z` : new Date(end).toISOString(),allDay,revision:event?.revision ?? 0,syncEnabled}
    return calendarApi.save(event?.id,input)
  },onSuccess:onDone})
  const remove = useMutation({mutationFn:() => calendarApi.remove(event!),onSuccess:onDone})
  const publish = useMutation({mutationFn:() => calendarApi.publish(event!.id,provider)})
  const busy = save.isPending || remove.isPending || publish.isPending
  return <section ref={section} className="page-stack" aria-label="Event details">
    <h2>{event ? 'Event details' : 'New event'}</h2>
    <form className="page-stack" onSubmit={action => {action.preventDefault();save.mutate()}}>
      <fieldset disabled={!canWrite || busy} className="page-stack">
        <label className="form-field">Event title<input required maxLength={160} value={title} onChange={action => setTitle(action.target.value)} /></label>
        <label className="form-field">Description<textarea maxLength={4000} rows={3} value={description} onChange={action => setDescription(action.target.value)} /></label>
        <label className="checkbox-label"><input type="checkbox" checked={allDay} onChange={action => {setAllDay(action.target.checked);setStart(action.target.checked ? start.slice(0,10) : `${start}T09:00`);setEnd(action.target.checked ? end.slice(0,10) : `${end}T10:00`)}} />All day</label>
        <label className="checkbox-label"><input type="checkbox" checked={syncEnabled} onChange={action => setSyncEnabled(action.target.checked)} />Include in my personal calendar sync</label>
        <p className="field-help">When personal sync is enabled, your own included events sync automatically. Turning this off pauses sync for this event and retains existing copies.</p>
        <div className="inline-actions"><label className="form-field">Starts<input type={allDay ? 'date' : 'datetime-local'} required min={allDay ? '1900-01-01' : '1900-01-01T00:00'} max={allDay ? '9999-12-31' : '9999-12-31T23:59'} value={start} onChange={action => setStart(action.target.value)} /></label><label className="form-field">Ends {allDay && '(exclusive date)'}<input type={allDay ? 'date' : 'datetime-local'} required value={end} onChange={action => setEnd(action.target.value)} /></label></div>
        <p className="field-help">{allDay ? 'The end date is the first day after the event.' : `Times use your browser’s time zone (${Intl.DateTimeFormat().resolvedOptions().timeZone}).`}</p>
        <Button type="submit" disabled={!title.trim() || !start || !end || end <= start}>Save event</Button>
      </fieldset>
    </form>
    {event && canWrite && <div className="inline-actions">{removeConfirm ? <><span>Delete this local event?</span><Button disabled={busy} onClick={() => remove.mutate()}>Confirm delete</Button><Button variant="ghost" onClick={() => setRemoveConfirm(false)}>Keep event</Button></> : <Button variant="ghost" disabled={busy} onClick={() => setRemoveConfirm(true)}>Delete event</Button>}</div>}
    {event && canWrite && providers.length > 0 && <div className="page-stack"><h3>Publish saved event</h3><p className="field-help">Creates a separate copy in the selected external calendar. Later local edits or deletion do not change that copy. No invitations are sent by this app.</p><label className="form-field">Destination calendar<select value={provider} onChange={action => {setProvider(action.target.value);publish.reset()}}><option value="">Choose a calendar</option>{providers.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label><Button disabled={busy || !provider} onClick={() => publish.mutate()}>Publish to calendar</Button>{publish.data && <p role="status">{publish.data.message}</p>}</div>}
    {(save.isError || remove.isError || publish.isError) && <p role="alert">{getErrorMessage(save.error ?? remove.error ?? publish.error,'Unable to save this event.')}</p>}
    <Button variant="ghost" disabled={busy} onClick={onDone}>Close event</Button>
  </section>
}
