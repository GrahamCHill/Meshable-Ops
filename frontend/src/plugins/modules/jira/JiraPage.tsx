import {ProjectPluginFrame} from '../../ProjectPluginFrame'
import {JiraProjectPanel} from './JiraProjectPanel'
export default function JiraPage(){return <ProjectPluginFrame title="Jira projects" description="Use Jira delivery metrics alongside your project workspace.">{(id,canManage)=><JiraProjectPanel projectId={id} canManage={canManage}/>}</ProjectPluginFrame>}
