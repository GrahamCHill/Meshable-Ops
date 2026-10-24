import {ProjectPluginFrame} from '../../ProjectPluginFrame'
import {FigmaProjectPanel} from './FigmaProjectPanel'
export default function FigmaPage(){return <ProjectPluginFrame title="Figma designs" description="Keep project designs and prototypes close to the work.">{(id,canManage)=><FigmaProjectPanel projectId={id} canManage={canManage}/>}</ProjectPluginFrame>}
