import {Link} from 'react-router-dom'
import type {CalendarEvent} from './api'

export function EventInformation({event}: {event:CalendarEvent}) {
  return <div className="calendar-event-information">
    <p className="calendar-description"><strong>Notes</strong><br/>{event.description||'No notes.'}</p>
    <p><strong>Calendar:</strong> {event.calendarName||event.source}{event.source==='calendar'&&!event.syncEnabled?' · Local only':''}</p>
    <div><strong>Linked documents</strong>{event.documents?.length?<ul>{event.documents.map((document,index)=><li key={`${document.url}:${index}`}>{document.url.startsWith('/')?<Link to={document.url}>{document.label}</Link>:<a href={document.url} target="_blank" rel="noreferrer">{document.label}</a>}</li>)}</ul>:<p>No linked documents.</p>}</div>
    <div><strong>Participants</strong>{event.participants?.length?<ul>{event.participants.map((person,index)=><li key={index}>{person.name}{person.name&&person.email?' · ':''}{person.email}{person.response?` (${person.response==='needsAction'||person.response==='notResponded'?'awaiting response':person.response})`:''}</li>)}</ul>:<p>No participant information.</p>}</div>
  </div>
}
