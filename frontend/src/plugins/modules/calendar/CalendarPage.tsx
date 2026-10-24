import { pluginRole } from '../../../api/access'
import {useMemo,useState} from 'react'
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query'
import {useAvailablePlugins} from '../../../api/plugins'
import {useAuthStore} from '../../../hooks/useAuthStore'
import {Button} from '../../../components/ui'
import {getErrorMessage} from '../../../components/admin/adminUtils'
import {calendarApi,type CalendarEvent} from './api'
import {EventInformation} from './EventInformation'
import {EventEditor} from './EventEditor'
import {CalendarGrid,CalendarEventLink} from './CalendarGrid'
import {calendarRange,dayString,shiftDate,type CalendarView} from './calendarRange'
import './calendar.css'

export default function CalendarPage() {
  const client=useQueryClient()
  const plugins=useAvailablePlugins()
  const role=useAuthStore(state => pluginRole(state.user?.role ?? '')) ?? ''
  const scope=useAuthStore(state => `${state.user?.id}:${state.user?.role}`)
  const policy=plugins.data?.find(plugin => plugin.manifest.id==='calendar')?.tableAccess.plugin_calendar_events
  const providers=role==='ADMIN' ? plugins.data?.filter(plugin => Object.prototype.hasOwnProperty.call(plugin.manifest.capabilities,'calendar.provider')).map(plugin => ({id:plugin.manifest.id,name:plugin.manifest.name})) ?? [] : []
  const [cursor,setCursor]=useState(() => new Date())
  const [view,setView]=useState<CalendarView>('month')
  const range=useMemo(() => calendarRange(cursor,view),[cursor,view])
  const [provider,setProvider]=useState('')
  const [editing,setEditing]=useState<{event?:CalendarEvent;date?:Date;allDay?:boolean}>()
  const selectedProvider=providers.find(item => item.id===provider)
  const from=range.start.toISOString(),until=range.end.toISOString()
  const readable=policy?.readRoles.includes(role)===true
  const boardAccess=plugins.data?.find(plugin => plugin.manifest.id==='kanban')?.tableAccess.plugin_kanban_boards?.readRoles.join(',') ?? ''
  const local=useQuery({queryKey:['plugin-calendar-events',scope,from,until,boardAccess],queryFn:() => calendarApi.window(from,until,''),enabled:readable})
  const remote=useQuery({queryKey:['plugin-calendar-events',scope,from,until,selectedProvider?.id],queryFn:() => calendarApi.window(from,until,selectedProvider!.id),enabled:readable && !!selectedProvider,retry:false})
  const events=useMemo(() => [...(local.data?.events ?? []),...(selectedProvider ? remote.data?.events ?? [] : [])].sort((a,b) => a.start.localeCompare(b.start)),[local.data,remote.data,selectedProvider])
  const exportCalendar=useMutation({mutationFn:async () => {
    const content=await calendarApi.export(from,until)
    const url=URL.createObjectURL(new Blob([content],{type:'text/calendar;charset=utf-8'}))
    const link=document.createElement('a');link.href=url;link.download='calendar.ics';link.click();setTimeout(() => URL.revokeObjectURL(url),1000)
  }})
  const done=() => {setEditing(undefined);void client.invalidateQueries({queryKey:['plugin-calendar-events']})}
  const shift=(offset: number) => {setCursor(shiftDate(cursor,view,offset));setEditing(undefined)}
  if (!readable) return <p role="alert">Your role cannot read calendar events.</p>
  return <div className="page-stack">
    <div><h1>Calendar</h1><p>Local events, personal calendar sync, and board deadlines.</p></div>
    <nav className="workspace-tabs" aria-label="Calendar view">{(['day','week','month'] as const).map(item => <button key={item} aria-pressed={view===item} className={view===item ? 'is-active' : ''} onClick={() => {setView(item);setEditing(undefined)}}>{item==='day' ? 'Day' : item==='week' ? 'Week' : 'Month'}</button>)}</nav>
    <div className="inline-actions"><Button variant="ghost" onClick={() => shift(-1)}>Previous {view}</Button><h2>{range.label}</h2><Button variant="ghost" onClick={() => shift(1)}>Next {view}</Button><Button variant="ghost" onClick={() => {setCursor(new Date());setEditing(undefined)}}>Today</Button></div>
    <label className="calendar-month-picker">Go to month<input type="month" aria-label="Go to month" value={dayString(cursor).slice(0,7)} onChange={e=>{if(!/^\d{4}-\d{2}$/.test(e.target.value))return;const [year,month]=e.target.value.split('-').map(Number);if(year<100||month<1||month>12)return;setCursor(new Date(year,month-1,1));setEditing(undefined)}}/></label>
    <div className="inline-actions">{policy?.writeRoles.includes(role) && <Button onClick={() => setEditing({date:new Date(cursor.getFullYear(),cursor.getMonth(),cursor.getDate(),9),allDay:false})}>New event</Button>}<Button variant="secondary" disabled={exportCalendar.isPending || local.isLoading || local.isError} onClick={() => exportCalendar.mutate()}>Export local events (.ics)</Button><Button variant="ghost" onClick={() => {void local.refetch();if (selectedProvider) void remote.refetch()}}>Refresh calendar</Button></div>
    {providers.length>0 && <label className="form-field">Show a shared external calendar<select value={selectedProvider?.id ?? ''} onChange={event => setProvider(event.target.value)}><option value="">Local events only</option>{providers.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>}
    {(local.isLoading || (!!selectedProvider && remote.isFetching)) && <p role="status">Loading calendar…</p>}
    {(local.isError || (!!selectedProvider && remote.isError) || exportCalendar.isError) && <p role="alert">{getErrorMessage(local.error ?? remote.error ?? exportCalendar.error,'Unable to load the selected calendar. Local events remain available when an external provider fails.')}</p>}
    {[local.data,selectedProvider ? remote.data : undefined].filter(Boolean).map((window,index) => <div key={index}>{window?.truncated && <p role="status">Only the first events are shown. Use Day or Week view for a smaller range.</p>}{window?.notice && <p role="status">{window.notice}</p>}</div>)}
    <CalendarGrid visibleMonth={dayString(cursor).slice(0,7)} view={view} days={range.days} events={events} onEdit={event=>setEditing({event})} onCreate={(date,allDay)=>setEditing({date,allDay})} canCreate={policy?.writeRoles.includes(role)===true} />
    <section className="page-stack"><h2>Agenda</h2><div className="calendar-agenda" role="region" aria-label="Agenda events">{events.length===0&&!local.isLoading&&<p>No events in this {view}.</p>}{events.map(event=><article className="calendar-agenda-event" key={event.id}><h3><CalendarEventLink event={event} onEdit={event=>setEditing({event})}/></h3><p>{event.allDay?`${event.start} · All day`:`${new Date(event.start).toLocaleString()} – ${new Date(event.end).toLocaleString()}`}</p><EventInformation event={event}/></article>)}</div></section>
    {editing && <EventEditor key={editing.event?.id ?? editing.date?.toISOString() ?? 'new'} event={editing.event} initialDate={editing.date} initialAllDay={editing.allDay} onDone={done} canWrite={policy?.writeRoles.includes(role)===true} providers={providers} />}
  </div>
}
