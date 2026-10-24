import type {CalendarEvent} from './api'
export type CalendarView='day'|'week'|'month'
export const dayString=(date: Date) => `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`
export function calendarRange(cursor: Date,view: CalendarView) {
  const start=new Date(cursor.getFullYear(),cursor.getMonth(),cursor.getDate())
  if (view==='month') start.setDate(1)
  if (view==='week') start.setDate(start.getDate()-(start.getDay()+6)%7)
  const end=new Date(start)
  if (view==='month') end.setMonth(end.getMonth()+1)
  else end.setDate(end.getDate()+(view==='week' ? 7 : 1))
  const days: Date[]=[]
  for (const date=new Date(start);date<end;date.setDate(date.getDate()+1)) days.push(new Date(date))
  const label=view==='month' ? start.toLocaleDateString(undefined,{month:'long',year:'numeric'}) : view==='day' ? start.toLocaleDateString(undefined,{weekday:'long',day:'numeric',month:'long',year:'numeric'}) : `${start.toLocaleDateString()} – ${days[days.length-1].toLocaleDateString()}`
  return {start,end,days,label}
}
export function shiftDate(cursor: Date,view: CalendarView,offset: number) {
  return view==='month' ? new Date(cursor.getFullYear(),cursor.getMonth()+offset,1) : new Date(cursor.getFullYear(),cursor.getMonth(),cursor.getDate()+offset*(view==='week' ? 7 : 1))
}
export function eventsOnDay(events: CalendarEvent[],date: Date) {
  const day=dayString(date),until=new Date(date.getFullYear(),date.getMonth(),date.getDate()+1)
  return events.filter(event => event.allDay ? event.start<=day && event.end>day : new Date(event.start)<until && new Date(event.end)>date)
}
