import type {ProjectSettings,Person} from '../../api/projects'
import {Button} from '../ui'

type Settings=ProjectSettings['settings']
export function ProjectSectionsSettings({value,onChange,people=[]}:{value:Settings;onChange:(change:Partial<Settings>)=>void;people?:Person[]}) {
  const move=(index:number,direction:number)=>{
    const sections=[...value.sections]
    ;[sections[index],sections[index+direction]]=[sections[index+direction],sections[index]]
    onChange({sections})
  }
  return <section className="page-stack"><h2>Project sections</h2>
    <p>Cards are grouped in this order. Rename sections to match your process; existing projects stay assigned. Move projects out of a section before removing it.</p>
    <label className="form-field">Default section for new projects<select value={value.defaultSectionId} onChange={event=>onChange({defaultSectionId:event.target.value})}>{value.sections.map((section,index)=><option key={section.id} value={section.id}>{section.name||`Section ${index+1}`}</option>)}</select></label>
    <label className="form-field">Inactive projects category<select value={value.inactiveSectionId??''} onChange={event=>onChange({inactiveSectionId:event.target.value||null})}><option value="">None</option>{value.sections.map(section=><option key={section.id} value={section.id}>{section.name}</option>)}</select></label>
    <fieldset className="page-stack"><legend>Who can move projects between categories</legend><p>Administrators retain access. Selected roles and users may move projects they can already access; this does not grant project membership or editing rights.</p><div className="project-choices">{['BUSINESS_MANAGER','USER'].map(role=><label key={role}><input type="checkbox" checked={(value.sectionMoveRoles??['ADMIN']).includes(role)} onChange={event=>onChange({sectionMoveRoles:event.target.checked?[...(value.sectionMoveRoles??['ADMIN']),role]:(value.sectionMoveRoles??['ADMIN']).filter(item=>item!==role)})}/>{role.replaceAll('_',' ')}</label>)}</div><div className="project-choices">{people.map(person=><label key={person.id}><input type="checkbox" checked={(value.sectionMoveUserIds??[]).includes(person.id)} onChange={event=>onChange({sectionMoveUserIds:event.target.checked?[...(value.sectionMoveUserIds??[]),person.id]:(value.sectionMoveUserIds??[]).filter(id=>id!==person.id)})}/>{person.name} · {person.email}</label>)}</div></fieldset>
    {value.sections.map((section,index)=><div className="project-section-setting" key={section.id}>
      <label className="form-field">Section {index+1} name<input required maxLength={80} value={section.name} onChange={event=>onChange({sections:value.sections.map(item=>item.id===section.id?{...item,name:event.target.value}:item)})}/></label>
      <div className="inline-actions"><Button type="button" variant="ghost" disabled={index===0} aria-label={`Move ${section.name||'section'} up`} onClick={()=>move(index,-1)}>Move up</Button><Button type="button" variant="ghost" disabled={index===value.sections.length-1} aria-label={`Move ${section.name||'section'} down`} onClick={()=>move(index,1)}>Move down</Button><Button type="button" variant="ghost" disabled={value.sections.length===1} onClick={()=>{
        const sections=value.sections.filter(item=>item.id!==section.id)
        onChange({sections,defaultSectionId:value.defaultSectionId===section.id?sections[0].id:value.defaultSectionId,inactiveSectionId:value.inactiveSectionId===section.id?null:value.inactiveSectionId})
      }}>Remove section</Button></div>
    </div>)}
    <div><Button type="button" variant="secondary" disabled={value.sections.length>=20} onClick={()=>onChange({sections:[...value.sections,{id:crypto.randomUUID(),name:''}]})}>Add section</Button></div>
  </section>
}
