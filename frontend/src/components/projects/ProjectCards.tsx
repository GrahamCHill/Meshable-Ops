import {Link} from 'react-router-dom'
import type {ProjectSummary,ProjectTag,ProjectSection} from '../../api/projects'
import {ProjectIcon} from './ProjectArtwork'

export function ProjectCards({projects,tags,sections=[],busy=false,onMove,onDrag}:{projects:ProjectSummary[];tags:ProjectTag[];sections?:ProjectSection[];busy?:boolean;onMove?:(project:ProjectSummary,section:string)=>void;onDrag?:(project:ProjectSummary|undefined)=>void}) {
  return <div className="project-cards" aria-label="Projects">{projects.map(project=><article key={project.id} className="project-card clickable-card" draggable={!!onDrag&&!!project.canMove&&!busy} onDragStart={event=>{if(!project.canMove||busy){event.preventDefault();return}event.dataTransfer.setData('text/plain',project.id);event.dataTransfer.effectAllowed='move';onDrag?.(project)}} onDragEnd={()=>onDrag?.(undefined)}>
    <div className="project-card-heading"><ProjectIcon id={project.id} value={project.artwork}/><div><h2><Link className="card-primary-link" draggable={false} to={`/projects/${project.id}`}>{project.name}</Link></h2><p>{project.clientId?<Link to={`/projects/clients/${project.clientId}`}>{project.clientName}</Link>:project.clientName}</p></div></div>
    <p className="project-card-category">{project.categoryName}</p>
    {!!project.tagIds.length&&<ul className="project-card-tags" aria-label="Project tags">{project.tagIds.map(id=>tags.find(tag=>tag.id===id)).filter(Boolean).map(tag=><li key={tag!.id}>{tag!.name}</li>)}</ul>}
    <small>Updated {new Date(project.updatedAt).toLocaleDateString()}</small>
    {project.canMove&&onMove&&<label className="form-field">Move project<select aria-label={'Move '+project.name+' to category'} disabled={busy} value={project.category} onChange={event=>onMove(project,event.target.value)}>{sections.map(section=><option key={section.id} value={section.id}>{section.name}</option>)}</select></label>}
  </article>)}</div>
}
