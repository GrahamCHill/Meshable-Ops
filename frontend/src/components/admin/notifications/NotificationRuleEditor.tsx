import { useState } from 'react'
import type { DeliveryMethodView, NotificationItem, NotificationRule } from '../../../api/notifications'
import { NotificationMessageEditor } from './NotificationMessageEditor'
import { Button } from '../../ui'

export function NotificationRuleEditor({ item, rule, methods, disabled, onChange }: { item: NotificationItem; rule: NotificationRule; methods: DeliveryMethodView[]; disabled: boolean; onChange: (rule: NotificationRule) => void }) {
  const eligible = methods.filter(value => item.eligibleMethods.includes(value.method.id))
  const [selected, setSelected] = useState(eligible[0]?.method.id ?? '')
  const method = eligible.find(value => value.method.id === selected)?.method
  const toggle = (id: string, checked: boolean, defaults = false) => {
    if (defaults) onChange({ ...rule, defaultMethods: checked ? [...rule.defaultMethods, id] : rule.defaultMethods.filter(value => value !== id) })
    else onChange({ ...rule, allowedMethods: checked ? [...rule.allowedMethods, id] : rule.allowedMethods.filter(value => value !== id), defaultMethods: checked ? rule.defaultMethods : rule.defaultMethods.filter(value => value !== id) })
  }
  return <section className="page-stack">
    <div><h2>{item.definition.label}</h2><p>{item.definition.description}</p></div>
    <fieldset className="page-stack" disabled={disabled}>
      <label className="checkbox-label"><input type="checkbox" checked={rule.enabled} onChange={e => onChange({ ...rule, enabled: e.target.checked })} />Enable this notification</label>
      {['GENERAL', 'WORKFLOW'].includes(item.definition.category) && <label className="checkbox-label"><input type="checkbox" checked={rule.allowMemberChoice} onChange={e => onChange({ ...rule, allowMemberChoice: e.target.checked })} />Allow members to choose their own delivery methods</label>}
      {item.definition.category === 'SECURITY' && <p className="field-help">Only verified direct recipients can receive codes. Email MFA uses Email; notification preferences cannot suppress it.</p>}
      <h3>Delivery choices</h3>
      {eligible.map(({ method, enabled, available }) => <div key={method.id} className="notification-method-choice"><label className="checkbox-label"><input type="checkbox" checked={rule.allowedMethods.includes(method.id)} onChange={e => toggle(method.id, e.target.checked)} />Allow {method.label}</label>{['GENERAL', 'WORKFLOW'].includes(item.definition.category) && <label className="checkbox-label"><input type="checkbox" checked={rule.defaultMethods.includes(method.id)} disabled={!rule.allowedMethods.includes(method.id)} onChange={e => toggle(method.id, e.target.checked, true)} />Selected by default</label>}<small>{!enabled ? 'Delivery method switched off' : !available ? 'Connection unavailable' : method.recipientKind === 'FIXED' ? 'Sends to the shared destination configured in the plugin' : 'Uses the recipient’s contact details'}</small></div>)}
    </fieldset>
    <nav className="inline-actions" aria-label="Message delivery methods">{eligible.map(({ method }) => <Button key={method.id} variant={selected === method.id ? 'secondary' : 'ghost'} aria-pressed={selected === method.id} onClick={() => setSelected(method.id)}>{method.label}</Button>)}</nav>
    {method && <NotificationMessageEditor key={`${item.definition.id}-${method.id}`} definition={item.definition} method={method} value={rule.templates[method.id]} disabled={disabled} onChange={template => onChange({ ...rule, templates: { ...rule.templates, [method.id]: template } })} />}
  </section>
}
