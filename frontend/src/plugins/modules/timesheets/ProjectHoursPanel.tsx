import {ProjectHours} from '../../operations/TimesheetsPage'
export default function ProjectHoursPanel({context}:{context:Record<string,unknown>}){return typeof context.id==='string'?<ProjectHours projectId={context.id}/>:null}
