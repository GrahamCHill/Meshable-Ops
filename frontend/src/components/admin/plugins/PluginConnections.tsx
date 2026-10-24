import type { PluginView } from '../../../api/plugins'

export function PluginConnections({plugin}: {plugin: PluginView}) {
  return <section className="page-stack">
    <h2>Plugin connections</h2>
    <p>{plugin.active ? 'Active for administrators.' : plugin.enabled ? 'Waiting for supporting plugins.' : 'Enable this plugin to use its connections.'} Connections also respect each signed-in user’s role.</p>
    {plugin.missingDependencies.length > 0 && <p role="status">Required: {plugin.missingDependencies.join(', ')}. Enable a supporting plugin to activate this plugin.</p>}
    {Object.keys(plugin.manifest.capabilities).length > 0 && <div><h3>Provides</h3><ul>{Object.entries(plugin.manifest.capabilities).map(([id,label]) => <li key={id}>{label} <small>({id})</small></li>)}</ul></div>}
    {plugin.features.map(feature => <div key={feature.id}><h3>{feature.label}</h3><p>{feature.available ? `Available through ${feature.providers.join(', ')}.` : feature.missing.length ? `Enable a plugin providing: ${feature.missing.join(', ')}.` : 'Enable this plugin to use this connection.'}</p></div>)}
    {!plugin.features.length && !plugin.manifest.requiresCapabilities.length && <p>No supporting plugins are required.</p>}
    <p className="field-help">Connections never enable plugins or grant account permissions automatically. Disabling a provider retains saved records and blocks connected operations. Configuration may still be required before using an active provider.</p>
  </section>
}
