import {useEffect,useRef,useState} from 'react'
import {useQuery,useQueryClient} from '@tanstack/react-query'
import {useSearchParams} from 'react-router-dom'
import {MessageSquare,Reply,Link2} from 'lucide-react'
import {boardsApi,type BoardCard,type BoardComment} from './api'
import {useAvailablePlugins} from '../../../api/plugins'
import {pluginRole} from '../../../api/access'
import {useAuthStore} from '../../../hooks/useAuthStore'
import {getErrorMessage} from '../../../components/admin/adminUtils'
import {usePageDraft} from '../../../hooks/usePageDraft'
import {MentionComposer,type CommentDraft} from './MentionComposer'
import {mentionPattern} from './mentionText'

function CommentText({text}: {text:string}) {
  const parts:React.ReactNode[]=[],pattern=new RegExp(mentionPattern.source,'g');let cursor=0
  for(const match of text.matchAll(pattern)){parts.push(text.slice(cursor,match.index));parts.push(<mark className="kanban-mention" key={match.index}>@{match[1]}</mark>);cursor=match.index!+match[0].length}
  parts.push(text.slice(cursor));return <p className="project-note">{parts}</p>
}
export function CardDiscussion({boardId,cards,locked,selectedCardId}: {boardId:string;cards:BoardCard[];locked:boolean;selectedCardId?:string}) {
  const [card,setCard]=useState(selectedCardId??''),[reply,setReply]=useState<string>(),[error,setError]=useState(''),[busy,setBusy]=useState(false)
  const [drafts,setDrafts]=usePageDraft<Record<string,CommentDraft>>(`discussion:${boardId}:${selectedCardId??'list'}`,{})
  const user=useAuthStore(state=>state.user),role=pluginRole(user?.role??''),plugins=useAvailablePlugins(),client=useQueryClient(),[params]=useSearchParams()
  const table=plugins.data?.find(plugin=>plugin.manifest.id==='kanban')?.tableAccess.plugin_kanban_comments
  const canRead=table?.readRoles.includes(role)===true,canWrite=table?.writeRoles.includes(role)===true
  const comments=useQuery({queryKey:['kanban-comments',user?.id,boardId,card],queryFn:()=>boardsApi.comments(boardId,card),enabled:!!card&&canRead})
  const people=useQuery({queryKey:['board-members',user?.id,boardId],queryFn:()=>boardsApi.members(boardId),enabled:canRead})
  const targeted=params.get('comment'),handled=useRef(false)
  useEffect(()=>{if(targeted&&!handled.current&&comments.data?.some(item=>item.id===targeted)){handled.current=true;setReply(targeted);requestAnimationFrame(()=>document.getElementById('comment-'+targeted)?.scrollIntoView({block:'center'}))}},[targeted,comments.data])
  const draftKey=reply??'root',draft=drafts[draftKey]??{text:'',mentions:[]}
  const add=async(text:string)=>{setBusy(true);setError('');try{await boardsApi.comment(boardId,card,text,[],reply);setDrafts({...drafts,[draftKey]:{text:'',mentions:[]}});setReply(undefined);await client.invalidateQueries({queryKey:['kanban-comments',user?.id,boardId,card]})}catch(failure){setError(getErrorMessage(failure))}finally{setBusy(false)}}
  const permalink=(comment:BoardComment)=>{const next=new URL(window.location.href);next.searchParams.set('board',boardId);next.searchParams.set('card',card);next.searchParams.set('comment',comment.id);next.hash='comment-'+comment.id;return next.pathname+next.search+next.hash}
  const render=(comment:BoardComment,depth=0):React.ReactNode=><article id={'comment-'+comment.id} className={'kanban-comment'+(targeted===comment.id?' is-targeted':'')} key={comment.id} style={{marginLeft:depth?12:0}}>
    <div className="kanban-comment-heading"><span className="kanban-avatar">{comment.authorName.split(/\s+/).slice(0,2).map(part=>part[0]).join('')}</span><strong>{comment.authorName}</strong><time dateTime={comment.createdAt}>{new Date(comment.createdAt).toLocaleString()}</time><a href={permalink(comment)} aria-label={'Link to comment by '+comment.authorName}><Link2 size={13}/></a></div>
    <CommentText text={comment.text}/>{canWrite&&<button type="button" className="kanban-comment-reply" disabled={busy||locked} onClick={()=>{setReply(comment.id);requestAnimationFrame(()=>document.querySelector<HTMLTextAreaElement>('.kanban-comment-composer textarea')?.focus())}}><Reply size={13}/>Reply</button>}
    {(comments.data??[]).filter(item=>item.parentId===comment.id).map(child=>render(child,Math.min(depth+1,5)))}
  </article>
  if(!canRead)return null
  return <section className="page-stack kanban-discussion"><div className="page-heading"><h3><MessageSquare size={16}/>Discussion</h3><small>{comments.data?.length??0} comments</small></div>
    {!selectedCardId&&<label className="form-field">Ticket<select disabled={locked||busy} value={card} onChange={event=>{setCard(event.target.value);setReply(undefined);setError('')}}><option value="">Choose a saved ticket</option>{cards.map(item=><option key={item.id} value={item.id}>{item.reference} {item.title}</option>)}</select></label>}
    {comments.isLoading&&<p role="status">Loading discussion…</p>}{comments.isError&&<p role="alert">Unable to load comments. <button type="button" onClick={()=>void comments.refetch()}>Retry</button></p>}
    <div className="kanban-comment-thread">{comments.data?.filter(comment=>!comment.parentId||!comments.data.some(parent=>parent.id===comment.parentId)).map(comment=>render(comment))}</div>
    {!comments.isLoading&&!comments.data?.length&&<p className="field-help">Start a discussion, ask a question, or @mention someone for input.</p>}
    {card&&canWrite&&<MentionComposer value={draft} onChange={value=>setDrafts({...drafts,[draftKey]:value})} people={people.data??[]} busy={busy||locked} replying={comments.data?.find(item=>item.id===reply)?.authorName} onCancel={()=>setReply(undefined)} onSubmit={text=>void add(text)}/>}
    {error&&<p role="alert">{error}</p>}
  </section>
}
