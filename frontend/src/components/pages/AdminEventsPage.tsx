import {useEffect,useRef} from 'react'
import {useInfiniteQuery,useQueryClient} from '@tanstack/react-query'
import {eventsApi,type Event} from '../../api/events'
import {useAuthStore} from '../../hooks/useAuthStore'
import {AdminPageHeader} from '../admin/AdminPageHeader'
import {Button,Spinner} from '../ui'
import '../../styles/events.css'

export function AdminEventsPage() {
  const userId=useAuthStore(state=>state.user?.id),client=useQueryClient()
  const scroll=useRef<HTMLDivElement>(null),trigger=useRef<HTMLDivElement>(null)
  const queryKey=['admin-events',userId]
  const history=useInfiniteQuery({queryKey,initialPageParam:undefined as string|undefined,
    queryFn:({pageParam,signal})=>eventsApi.history(pageParam,signal),getNextPageParam:page=>page.nextCursor??undefined,
    refetchOnWindowFocus:false,refetchOnMount:false})
  const {hasNextPage,isFetching,isFetchNextPageError,fetchNextPage}=history
  useEffect(()=>{
    if(!hasNextPage||isFetching||isFetchNextPageError||!trigger.current||!scroll.current||typeof IntersectionObserver==='undefined')return
    const observer=new IntersectionObserver(entries=>{if(entries.some(entry=>entry.isIntersecting))void fetchNextPage()},
      {root:scroll.current,rootMargin:'160px'})
    observer.observe(trigger.current)
    return()=>observer.disconnect()
  },[hasNextPage,isFetching,isFetchNextPageError,fetchNextPage])
  const events=history.data?.pages.flatMap(page=>page.content)??[]
  const refresh=()=>{scroll.current?.scrollTo({top:0});void client.resetQueries({queryKey,exact:true})}
  return <div className="page-stack admin-events-page">
    <AdminPageHeader title="Event log" description="Scroll to load older events. Refresh to see the latest activity."
      actions={<Button variant="secondary" disabled={isFetching} onClick={refresh}>Refresh newest events</Button>}/>
    {history.isPending&&<div role="status"><Spinner/> Loading event history…</div>}
    {history.isError&&!history.isFetchNextPageError&&<p role="alert">Event history could not load. <button onClick={refresh}>Try again</button></p>}
    {history.data&&<>
      <p className="field-help" role="status">{events.length.toLocaleString()} events loaded{hasNextPage?' · Scroll for more':''}</p>
      <div ref={scroll} className="event-history" role="region" aria-label="Event history" aria-busy={isFetching}>
        {events.map(event=><EventRow key={event.id} event={event}/>)}
        <div ref={trigger} className="event-history-more">
          {history.isFetchNextPageError&&<p role="alert">Older events could not load. The events above are still available.</p>}
          {hasNextPage?<Button variant="ghost" disabled={isFetching} onClick={()=>void fetchNextPage()}>{history.isFetchingNextPage?'Loading older events…':history.isFetchNextPageError?'Retry loading older events':'Load older events'}</Button>
            :<p>{events.length?'You have reached the beginning of the event history.':'No events have been recorded yet.'}</p>}
        </div>
      </div>
    </>}
  </div>
}

function EventRow({event}:{event:Event}) {
  const time=event.createdAt?new Date(event.createdAt).toLocaleString():'Unknown time'
  return <article className="event-history-entry">
    <div className="event-history-heading"><strong>{event.type}</strong><time dateTime={event.createdAt}>{time}</time></div>
    {Object.keys(event.payload).length>0&&<details><summary aria-label={`Details for ${event.type} at ${time}`}>Details</summary><pre>{JSON.stringify(event.payload,null,2)}</pre></details>}
  </article>
}
