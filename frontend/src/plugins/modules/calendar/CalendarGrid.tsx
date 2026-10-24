import {Link} from 'react-router-dom'
import type {CalendarEvent} from './api'
import {dayString,eventsOnDay,type CalendarView} from './calendarRange'

export function CalendarEventLink({event,onEdit}: {event: CalendarEvent; onEdit: (event: CalendarEvent) => void}) {
  return event.source==='calendar' ? <button onClick={() => onEdit(event)}>{event.title}</button> : event.href ? <Link to={event.href}>{event.title}</Link> : <span>{event.title}</span>
}
export function CalendarGrid({view,days,events,onEdit}: {view: CalendarView; days: Date[]; events: CalendarEvent[]; onEdit: (event: CalendarEvent) => void}) {
  return <div className={`calendar-grid calendar-${view}`} aria-label={`${view} calendar`}>
    {view==='month' && <>{['Mon','Tue','Wed','Thu','Fri','Sat','Sun'].map(day => <strong key={day}>{day}</strong>)}{Array.from({length:(days[0].getDay()+6)%7},(_,index) => <div key={`empty-${index}`} />)}</>}
    {days.map(date => <section key={dayString(date)} aria-label={dayString(date)} className="calendar-day"><time dateTime={dayString(date)}>{view==='month' ? date.getDate() : date.toLocaleDateString(undefined,{weekday:'short',day:'numeric',month:'short'})}</time>{eventsOnDay(events,date).map(event => <div key={event.id} className="page-stack"><CalendarEventLink event={event} onEdit={onEdit} /><small>{event.allDay ? 'All day' : new Date(event.start).toLocaleTimeString(undefined,{hour:'2-digit',minute:'2-digit'})}</small>{view==='day' && event.description && <p className="calendar-description">{event.description}</p>}</div>)}</section>)}
  </div>
}
