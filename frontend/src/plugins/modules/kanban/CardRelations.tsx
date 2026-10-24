import {useState} from 'react'
import {Link2,Plus,X} from 'lucide-react'
import type {BoardCard,TicketLink,TicketLinkType} from './api'
import {TicketTypeIcon} from './TicketTile'
import {Button} from '../../../components/ui'

export const relationLabels:Record<TicketLinkType,string>={RELATED:'Relates to',BLOCKS:'Blocks',BLOCKED_BY:'Is blocked by'}
const reverse=(type:TicketLinkType):TicketLinkType=>type==='BLOCKS'?'BLOCKED_BY':type==='BLOCKED_BY'?'BLOCKS':'RELATED'
export function CardRelations({card,cards,onChange,onOpen,readOnly,busy}: {card:BoardCard;cards:BoardCard[];onChange:(links:TicketLink[])=>void;onOpen?:(card:BoardCard)=>void;readOnly:boolean;busy:boolean}) {
  const [adding,setAdding]=useState(false),[query,setQuery]=useState(''),[type,setType]=useState<TicketLinkType>('RELATED'),[target,setTarget]=useState('')
  const own=(card.links??[]).map(link=>({...link,incoming:false}))
  const incoming=cards.flatMap(other=>(other.links??[]).filter(link=>link.targetId===card.id).map(link=>({targetId:other.id,type:reverse(link.type),incoming:true})))
  const links=[...own,...incoming].filter((link,index,all)=>all.findIndex(item=>item.targetId===link.targetId&&item.type===link.type)===index)
  const options=cards.filter(item=>item.id!==card.id&&[item.reference,item.title].join(' ').toLowerCase().includes(query.toLowerCase()))
  return <section className="page-stack kanban-linked-tickets"><div className="page-heading"><h3><Link2 size={16}/>Linked tickets</h3>{!readOnly&&<Button type="button" size="sm" variant="ghost" disabled={busy} onClick={()=>setAdding(value=>!value)}><Plus size={14}/>Link ticket</Button>}</div>
    {links.map(link=>{const other=cards.find(item=>item.id===link.targetId);if(!other)return null;return <div className="kanban-linked-ticket" key={link.targetId+link.type}><span>{relationLabels[link.type]}</span><button type="button" disabled={busy} onClick={()=>onOpen?.(other)}><TicketTypeIcon type={other.type}/><strong>{other.reference}</strong>{other.title}</button><small>{other.status==='DONE'?'Done':'Open'}</small>{!readOnly&&!link.incoming&&<button type="button" disabled={busy} aria-label={`Unlink ${other.reference}`} onClick={()=>onChange((card.links??[]).filter(item=>item.targetId!==link.targetId||item.type!==link.type))}><X size={14}/></button>}{link.incoming&&<small title="Open the linked ticket to edit this relationship">Linked from ticket</small>}</div>})}
    {!links.length&&<p className="field-help">Connect related work and identify tickets that block delivery.</p>}
    {adding&&!readOnly&&<div className="kanban-link-picker page-stack"><label className="form-field">Relationship<select value={type} onChange={event=>setType(event.target.value as TicketLinkType)}>{Object.entries(relationLabels).map(([value,label])=><option key={value} value={value}>{label}</option>)}</select></label><label className="form-field">Find a ticket<input type="search" placeholder="Reference or title" value={query} onChange={event=>setQuery(event.target.value)}/></label><label className="form-field">Ticket<select value={target} onChange={event=>setTarget(event.target.value)}><option value="">Choose a ticket</option>{options.map(item=><option key={item.id} value={item.id}>{item.reference} · {item.title}</option>)}</select></label><div className="inline-actions"><Button type="button" size="sm" disabled={busy||!target||(card.links??[]).some(link=>link.targetId===target&&link.type===type)} onClick={()=>{onChange([...(card.links??[]),{targetId:target,type}]);setAdding(false);setTarget('');setQuery('')}}>Add link</Button><Button type="button" variant="ghost" size="sm" onClick={()=>setAdding(false)}>Cancel</Button></div><small className="field-help">Links are saved with the ticket. Blocking cycles are rejected.</small></div>}
  </section>
}
