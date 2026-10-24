import {ProjectPluginFrame} from '../../ProjectPluginFrame'
import {MermaidProjectPanel} from './MermaidProjectPanel'
export default function MermaidPage(){return <ProjectPluginFrame title="Mermaid diagrams" description="Document project flows and architecture as diagrams.">{(id,canManage)=><MermaidProjectPanel projectId={id} canManage={canManage}/>}</ProjectPluginFrame>}
