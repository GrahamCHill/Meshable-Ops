import {ArrowUp,ArrowDown} from 'lucide-react'
import {moveNavigation,ordered} from '../../api/navigation'

export function NavigationOrderList({label,items,order,onChange,disabled}:{label:string;items:{id:string;label:string;path:string}[];order:string[];onChange:(order:string[])=>void;disabled?:boolean}) {
  const sorted=ordered(items,order), known=items.map(item=>item.id)
  return <ol className="navigation-order-list" aria-label={label}>{sorted.map((item,index)=><li key={item.id}>
    <span className="navigation-position" aria-hidden="true">{index+1}</span><div className="navigation-order-name"><strong>{item.label}</strong><small>{item.path}</small></div>
    <div className="navigation-order-actions"><button type="button" aria-label={`Move ${item.label} up`} title="Move up" disabled={disabled||index===0} onClick={()=>onChange(moveNavigation(order,known,item.id,sorted[index-1].id))}><ArrowUp size={17} aria-hidden="true"/></button>
    <button type="button" aria-label={`Move ${item.label} down`} title="Move down" disabled={disabled||index===sorted.length-1} onClick={()=>onChange(moveNavigation(order,known,item.id,sorted[index+1].id))}><ArrowDown size={17} aria-hidden="true"/></button></div>
  </li>)}</ol>
}
