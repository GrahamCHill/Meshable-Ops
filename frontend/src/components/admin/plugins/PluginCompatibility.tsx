import type {PluginView} from '../../../api/plugins'

export function PluginCompatibility({plugin,compact=false}: {plugin:PluginView;compact?:boolean}) {
  return <div className="page-stack" aria-label="Application compatibility">
    <p><strong>{plugin.versionCompatibility==='COMPATIBLE'?'App version supported':plugin.versionCompatibility==='INCOMPATIBLE'?'Outside declared app versions — proceed with caution':'Compatibility unverified'}</strong> · Meshable Ops {plugin.applicationVersion}</p>
    {!compact&&!!plugin.manifest.compatibleVersions?.length&&<p>Supported application versions: {plugin.manifest.compatibleVersions.join(', ')}</p>}
    {plugin.compatibilityWarnings?.map(warning=><p role="status" className="field-help" key={warning}>{warning}</p>)}
    {plugin.compatibilityIssues?.map(issue=><p role="alert" key={issue}>{issue}</p>)}
  </div>
}
