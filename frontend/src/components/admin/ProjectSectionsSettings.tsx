import type {ProjectSettings} from '../../api/projects'
import {Button} from '../ui'

type Settings=ProjectSettings['settings']
export function ProjectSectionsSettings({value,onChange}:{value:Settings;onChange:(change:Partial<Settings>)=>void}) {
  const move=(index:number,direction:number)=>{
    const sections=[...value.sections]
    ;[sections[index],sections[index+direction]]=[sections[index+direction],sections[index]]
    onChange({sections})
  }
  return <section className="page-stack"><h2>Project sections</h2>
    <p>Cards are grouped in this order. Rename sections to match your process; existing projects stay assigned. Move projects out of a section before removing it.</p>
    <label className="form-field">Default section for new projects<select value={value.defaultSectionId} onChange={event=>onChange({defaultSectionId:event.target.value})}>{value.sections.map((section,index)=><option key={section.id} value={section.id}>{section.name||`Section ${index+1}`}</option>)}</select></label>
    {value.sections.map((section,index)=><div className="project-section-setting" key={section.id}>
      <label className="form-field">Section {index+1} name<input required maxLength={80} value={section.name} onChange={event=>onChange({sections:value.sections.map(item=>item.id===section.id?{...item,name:event.target.value}:item)})}/></label>
      <div className="inline-actions"><Button type="button" variant="ghost" disabled={index===0} aria-label={`Move ${section.name||'section'} up`} onClick={()=>move(index,-1)}>Move up</Button><Button type="button" variant="ghost" disabled={index===value.sections.length-1} aria-label={`Move ${section.name||'section'} down`} onClick={()=>move(index,1)}>Move down</Button><Button type="button" variant="ghost" disabled={value.sections.length===1} onClick={()=>{
        const sections=value.sections.filter(item=>item.id!==section.id)
        onChange({sections,defaultSectionId:value.defaultSectionId===section.id?sections[0].id:value.defaultSectionId})
      }}>Remove section</Button></div>
    </div>)}
    <div><Button type="button" variant="secondary" disabled={value.sections.length>=20} onClick={()=>onChange({sections:[...value.sections,{id:crypto.randomUUID(),name:''}]})}>Add section</Button></div>
  </section>
}
