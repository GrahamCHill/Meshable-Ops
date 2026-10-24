import {Link} from 'react-router-dom'
import {useQuery} from '@tanstack/react-query'
import {ExternalLink,Columns3} from 'lucide-react'
import {useAvailablePlugins} from '../../api/plugins'
import {pluginRole} from '../../api/access'
import {useAuthStore} from '../../hooks/useAuthStore'
import {boardsApi} from '../../plugins/modules/kanban/api'
import {jiraApi} from '../../plugins/modules/jira/api'

export function ProjectBoardLinks({projectId,canManage}:{projectId:string;canManage:boolean}) {
  const plugins=useAvailablePlugins(),user=useAuthStore(state=>state.user),role=pluginRole(user?.role??'')??''
  const kanban=plugins.data?.some(plugin=>plugin.manifest.id==='kanban'&&plugin.tableAccess.plugin_kanban_boards?.readRoles.includes(role))??false
  const jira=plugins.data?.some(plugin=>plugin.manifest.id==='jira'&&plugin.tableAccess.plugin_jira_projects?.readRoles.includes(role))??false
  const boards=useQuery({queryKey:['project-board-shortcuts',user?.id,projectId],queryFn:()=>boardsApi.list(projectId),enabled:kanban})
  const links=useQuery({queryKey:['jira-project-links',user?.id,projectId],queryFn:()=>jiraApi.list(projectId),enabled:jira})
  if(!kanban&&!jira)return null
  return <section className="page-stack project-board-shortcuts" aria-label="Project boards"><div className="page-heading"><h2><Columns3 size={18}/> Project boards</h2><div className="inline-actions">{kanban&&<Link to={'/plugins/kanban/boards?project='+projectId}>All project boards</Link>}{jira&&canManage&&<Link to={'/plugins/jira?project='+projectId}>Manage Jira boards</Link>}</div></div><div className="project-board-link-list">
    {kanban&&boards.data?.map(board=><Link key={board.id} className="project-board-shortcut" to={'/plugins/kanban/boards?board='+board.id}><small>Kanban</small><strong>{board.name}</strong><span>Open board →</span></Link>)}
    {jira&&links.data?.map(link=><div className="project-board-shortcut" key={link.id}><small>Jira · {link.key}</small><strong>{link.label}</strong>{link.boardUrl?<a href={link.boardUrl} target="_blank" rel="noopener noreferrer">Open Jira board <ExternalLink size={13}/></a>:<Link to={'/plugins/jira?project='+projectId}>{canManage?'Choose Jira board':'View Jira project'}</Link>}</div>)}
  </div>{(kanban&&boards.isLoading||jira&&links.isLoading)&&<p role="status">Loading project boards…</p>}{kanban&&boards.isError&&<p role="alert">Kanban boards could not load. <button type="button" onClick={()=>void boards.refetch()}>Retry</button></p>}{jira&&links.isError&&<p role="alert">Jira boards could not load. <button type="button" onClick={()=>void links.refetch()}>Retry</button></p>}{kanban&&boards.isSuccess&&!boards.data.length&&<p>No Kanban boards are linked to this project.</p>}</section>
}
