import { Link } from 'react-router-dom'
import {useAvailablePlugins} from '../../../api/plugins'
import {useAccess} from '../../../api/access'
import {hasPluginSettings,pluginSettingsPath} from '../../../lib/pluginAdmin'
import type { DeliveryMethodView } from '../../../api/notifications'

export function NotificationMethods({ methods, enabled, disabled, onChange }: { methods: DeliveryMethodView[]; enabled: Record<string, boolean>; disabled: boolean; onChange: (id: string, value: boolean) => void }) {
  const plugins=useAvailablePlugins(),access=useAccess()
  const settingsFor=(id:string|null)=>{const plugin=plugins.data?.find(p=>p.manifest.id===id);return plugin&&hasPluginSettings(plugin)&&access.can('admin.plugins')?pluginSettingsPath(plugin):undefined}
  return <section className="page-stack"><h2>Delivery methods</h2><p>Enable a method here, then allow it on the notifications that should use it. Plugins supply their own connection settings.</p>{methods.map(({ method, available }) => <div className="page-stack" key={method.id}><div className="inline-actions"><h3 style={{ flex: 1 }}>{method.label}</h3>{!method.pluginId?<Link to="/admin/email">Configure email delivery</Link>:settingsFor(method.pluginId)&&<Link to={settingsFor(method.pluginId)!}>Plugin settings</Link>}</div><label className="checkbox-label"><input type="checkbox" disabled={disabled} checked={enabled[method.id]} onChange={e => onChange(method.id, e.target.checked)} />Enable {method.label}</label><small>{available ? 'Connection available' : 'Connection unavailable — complete the delivery settings'} · {method.recipientKind === 'FIXED' ? 'Shared destination' : method.recipientKind === 'PHONE' ? 'Phone number' : 'Email address'}{method.supportsOtp && ' · Verification codes supported'}{method.supportsHtml && ' · HTML supported'}</small></div>)}<p className="field-help">Additional methods, including SMS providers, appear when their plugin registers a notification adapter. Disabling a method preserves its templates and connection settings.</p></section>
}
