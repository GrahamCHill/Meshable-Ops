import {Link} from 'react-router-dom'
import type {ProjectSummary,ProjectTag} from '../../api/projects'
import {ProjectIcon} from './ProjectArtwork'

export function ProjectCards({projects,tags}:{projects:ProjectSummary[];tags:ProjectTag[]}) {
  return <div className="project-cards" aria-label="Projects">{projects.map(project=><article key={project.id} className="project-card">
    <div className="project-card-heading"><ProjectIcon id={project.id} value={project.artwork}/><div><h2><Link to={`/projects/${project.id}`}>{project.name}</Link></h2><p>{project.clientId?<Link to={`/projects/clients/${project.clientId}`}>{project.clientName}</Link>:project.clientName}</p></div></div>
    <p className="project-card-category">{project.categoryName}</p>
    {!!project.tagIds.length&&<ul className="project-card-tags" aria-label="Project tags">{project.tagIds.map(id=>tags.find(tag=>tag.id===id)).filter(Boolean).map(tag=><li key={tag!.id}>{tag!.name}</li>)}</ul>}
    <small>Updated {new Date(project.updatedAt).toLocaleDateString()}</small>
  </article>)}</div>
}
