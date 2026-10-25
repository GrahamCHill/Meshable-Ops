import type {PluginView} from '../../api/plugins'
import {AnalyticsScope} from './AnalyticsScope'
import {AnalyticsPanel} from './AnalyticsPanel'
export default function AnalyticsExtension({plugin,context}:{plugin:PluginView;slot:string;context:Record<string,unknown>}) {
  const {kind,id}=context
  if((kind!=='CLIENT'&&kind!=='PROJECT')||typeof id!=='string')return null
  return <AnalyticsScope kind={kind} id={id}>{canManage=><AnalyticsPanel plugin={plugin} kind={kind} contextId={id} canManage={canManage}/>}</AnalyticsScope>
}
