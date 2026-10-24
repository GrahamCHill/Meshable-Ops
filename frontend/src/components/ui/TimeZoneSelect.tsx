import {useId,useMemo,useState} from 'react'
import {timeZoneOptions} from '../../lib/timezones'

export function TimeZoneSelect({value,onChange,id,disabled=false,required=true}:{value:string;onChange:(value:string)=>void;id?:string;disabled?:boolean;required?:boolean}) {
  const generated=useId(),fieldId=id??`timezone-${generated}`
  const [filter,setFilter]=useState('')
  const options=useMemo(()=>timeZoneOptions(value),[value])
  const groups=useMemo(()=>{
    const matches=options.filter(option=>option.id===value||`${option.label} ${option.id}`.toLowerCase().includes(filter.trim().toLowerCase()))
    return [...new Set(matches.map(option=>option.group))].map(group=>({label:group,options:matches.filter(option=>option.group===group)}))
  },[options,filter,value])
  return <div className="form-field timezone-select"><label htmlFor={fieldId}>Timezone</label>
    <select id={fieldId} required={required} disabled={disabled} value={value} onChange={event=>onChange(event.target.value)} aria-describedby={`${fieldId}-help`}>
      {!value&&<option value="">Choose a timezone</option>}{groups.map(group=><optgroup key={group.label} label={group.label}>{group.options.map(option=><option key={option.id} value={option.id}>{option.label}</option>)}</optgroup>)}
    </select>
    <details><summary>Find a timezone</summary><label htmlFor={`${fieldId}-filter`}>Search names or cities</label><input id={`${fieldId}-filter`} type="search" value={filter} disabled={disabled} onChange={event=>setFilter(event.target.value)} placeholder="For example: London, South Africa, Pacific"/>{filter&&<small>{groups.reduce((sum,group)=>sum+group.options.filter(option=>option.id!==value).length,0)} other matching timezones. Your current selection stays available.</small>}</details>
    <small id={`${fieldId}-help`}>Offsets shown are current. Times adjust for daylight saving where applicable.</small>
  </div>
}
