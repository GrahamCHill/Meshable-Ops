import {useRef,useState} from 'react'
import type {Person} from '../../../api/projects'
import {Button} from '../../../components/ui'
import {mentionTrigger,serializeMentions,type MentionChoice} from './mentionText'

export interface CommentDraft {text:string;mentions:MentionChoice[]}
export function MentionComposer({value,onChange,people,busy,onSubmit,replying,onCancel}: {value:CommentDraft;onChange:(value:CommentDraft)=>void;people:Person[];busy:boolean;onSubmit:(text:string)=>void;replying?:string;onCancel:()=>void}) {
  const input=useRef<HTMLTextAreaElement>(null),[caret,setCaret]=useState(0),[selected,setSelected]=useState(0),[dismissed,setDismissed]=useState(false)
  const trigger=dismissed?undefined:mentionTrigger(value.text,caret)
  const matches=trigger?people.filter(person=>[person.name,person.email].join(' ').toLowerCase().includes(trigger.query.toLowerCase())).slice(0,8):[]
  const insert=(person:Person)=>{
    if(!trigger)return
    const raw=person.name.replace(/[\]\r\n]/g,' '),label=people.filter(item=>item.name===person.name).length>1?`${raw} (${person.email})`:raw
    const text=value.text.slice(0,trigger.start)+'@'+label+' '+value.text.slice(trigger.end),position=trigger.start+label.length+2
    onChange({text,mentions:[...value.mentions.filter(item=>item.label!==label),{id:person.id,label}]});setCaret(position);setDismissed(true)
    requestAnimationFrame(()=>{input.current?.focus();input.current?.setSelectionRange(position,position)})
  }
  const serialized=serializeMentions(value.text,value.mentions)
  return <form className="page-stack kanban-comment-composer" onSubmit={event=>{event.preventDefault();if(!busy&&serialized.trim()&&serialized.length<=4000)onSubmit(serialized)}}>
    <label className="form-field">{replying?'Reply to '+replying:'Add to the discussion'}<textarea ref={input} rows={4} maxLength={4000} disabled={busy} value={value.text} placeholder="Write a comment… Type @ to mention someone." aria-autocomplete="list" aria-controls={matches.length?'ticket-mention-options':undefined} aria-expanded={!!matches.length} aria-activedescendant={matches.length?'mention-option-'+Math.min(selected,matches.length-1):undefined}
      onChange={event=>{onChange({...value,text:event.target.value});setCaret(event.target.selectionStart);setSelected(0);setDismissed(false)}} onSelect={event=>setCaret(event.currentTarget.selectionStart)} onKeyDown={event=>{
        if(matches.length&&event.key==='ArrowDown'){event.preventDefault();setSelected(index=>(index+1)%matches.length)}
        else if(matches.length&&event.key==='ArrowUp'){event.preventDefault();setSelected(index=>(index+matches.length-1)%matches.length)}
        else if(matches.length&&event.key==='Enter'&&!event.shiftKey){event.preventDefault();insert(matches[Math.min(selected,matches.length-1)])}
        else if(matches.length&&event.key==='Escape'){event.preventDefault();event.stopPropagation();setDismissed(true)}
        else if(event.key==='Enter'&&(event.ctrlKey||event.metaKey)){event.preventDefault();event.currentTarget.form?.requestSubmit()}
      }}/></label>
    {matches.length>0&&<div role="listbox" id="ticket-mention-options" aria-label="Mention a board member" className="kanban-mention-options">{matches.map((person,index)=><button type="button" role="option" aria-selected={index===selected} id={'mention-option-'+index} key={person.id} onMouseDown={event=>event.preventDefault()} onClick={()=>insert(person)}><strong>{person.name}</strong><small>{person.email}</small></button>)}</div>}
    <div className="inline-actions"><Button type="submit" size="sm" loading={busy} disabled={!serialized.trim()||serialized.length>4000}>{replying?'Post reply':'Post comment'}</Button>{replying&&<Button type="button" size="sm" variant="ghost" disabled={busy} onClick={onCancel}>Cancel reply</Button>}<small className="field-help">Ctrl+Enter to post · {serialized.length}/4000</small></div>
    <small className="field-help">Choose a person from @ suggestions to notify them through their enabled communication preferences.</small>
  </form>
}
