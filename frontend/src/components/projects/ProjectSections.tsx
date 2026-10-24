import {useId} from 'react'
import type {ProjectSection,ProjectSummary,ProjectTag} from '../../api/projects'
import {ProjectCards} from './ProjectCards'

export function ProjectSections({projects,sections,tags,selected=''}:{projects:ProjectSummary[];sections:ProjectSection[];tags:ProjectTag[];selected?:string}) {
  const id=useId()
  // Retain visibility if metadata was refreshed between the directory and settings requests.
  const known=new Set(sections.map(section=>section.id))
  const extra=[...new Map(projects.filter(project=>!known.has(project.category)).map(project=>[project.category,{id:project.category,name:project.categoryName}])).values()]
  return <div className="page-stack project-sections">{[...sections,...extra].filter(section=>!selected||section.id===selected).map(section=>{
    const matches=projects.filter(project=>project.category===section.id)
    return <section key={section.id} className="page-stack" aria-labelledby={`${id}-${section.id}`}>
      <div className="project-section-heading"><h2 id={`${id}-${section.id}`}>{section.name}</h2><span>{matches.length} {matches.length===1?'project':'projects'}</span></div>
      {matches.length?<ProjectCards projects={matches} tags={tags}/>:<p className="field-help">No matching projects in this section.</p>}
    </section>
  })}</div>
}
