import type {CalendarEvent} from './api'

/** Assign overlap lanes within each connected time group, leaving unrelated events full width. */
export function timedLayout(events:CalendarEvent[],date:Date) {
  const next=new Date(date);next.setDate(next.getDate()+1)
  const rows=events.filter(event=>!event.allDay).map(event=>{const start=new Date(event.start),end=new Date(event.end);return {event,start:start<date?0:start.getHours()*60+start.getMinutes(),end:end>=next?1440:end.getHours()*60+end.getMinutes(),lane:0,lanes:1}}).sort((a,b)=>a.start-b.start||a.end-b.end)
  let group:typeof rows=[];let ends:number[]=[];let until=0
  const finish=()=>{group.forEach(row=>row.lanes=ends.length);group=[];ends=[];until=0}
  rows.forEach(row=>{if(group.length&&row.start>=until)finish();let lane=ends.findIndex(end=>end<=row.start);if(lane<0)lane=ends.length;row.lane=lane;ends[lane]=row.end;until=Math.max(until,row.end);group.push(row)})
  finish();return rows
}
