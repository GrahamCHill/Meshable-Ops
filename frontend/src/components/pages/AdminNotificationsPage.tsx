import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { notificationsApi, notificationInput, type NotificationInput } from '../../api/notifications'
import { useAuthStore } from '../../hooks/useAuthStore'
import { AdminPageHeader } from '../admin/AdminPageHeader'
import { NotificationRuleEditor } from '../admin/notifications/NotificationRuleEditor'
import { NotificationMethods } from '../admin/notifications/NotificationMethods'
import { NotificationHistory } from '../admin/notifications/NotificationHistory'
import { NotificationSendPanel } from '../admin/notifications/NotificationSendPanel'
import { getErrorMessage } from '../admin/adminUtils'
import { Button, Spinner } from '../ui'
import '../../styles/notifications.css'

export default function AdminNotificationsPage() {
  const client = useQueryClient(); const user = useAuthStore(state => state.user)
  const key = ['admin-notifications', user?.id]
  const settings = useQuery({ queryKey: key, queryFn: notificationsApi.settings, refetchInterval: 15000 })
  const [draft, setDraft] = useState<NotificationInput>(); const [dirty, setDirty] = useState(false); const [saving, setSaving] = useState(false)
  const [tab, setTab] = useState('notifications'); const [selectedId, setSelectedId] = useState('general.message'); const [filter, setFilter] = useState('')
  const [error, setError] = useState(''); const [notice, setNotice] = useState('')
  useEffect(() => { if (settings.data && !dirty) setDraft(notificationInput(settings.data)) }, [settings.data, dirty])
  const change = (next: NotificationInput) => { setDraft(next); setDirty(true); setError(''); setNotice('') }
  const save = async () => { if (!draft) return; setSaving(true); setError(''); setNotice(''); try { const saved = await notificationsApi.save(draft); client.setQueryData(key, saved); setDraft(notificationInput(saved)); setDirty(false); setNotice('Notification settings saved.'); await Promise.all([client.invalidateQueries({ queryKey: ['notification-availability'] }), client.invalidateQueries({ queryKey: ['notification-preferences'] })]) } catch (failure) { setError(getErrorMessage(failure, 'Unable to save notification settings.')) } finally { setSaving(false) } }
  const selected = settings.data?.notifications.find(item => item.definition.id === selectedId)
  return <div className="page-stack">
    <AdminPageHeader title="Notifications" description="Choose delivery methods, customise each message, and control workflow alerts." actions={<Link className="button button-ghost button-sm" to="/account/notifications">My notification preferences</Link>} />
    <nav className="inline-actions" aria-label="Notification settings sections">{[['notifications', 'Notifications'], ['methods', 'Delivery methods'], ['history', 'Delivery history'], ['send', 'Send message']].map(([id, label]) => <Button key={id} variant={tab === id ? 'secondary' : 'ghost'} aria-pressed={tab === id} onClick={() => setTab(id)}>{label}</Button>)}</nav>
    {settings.isLoading && <Spinner />}{settings.isError && <p role="alert">Unable to load notification settings. <Button variant="ghost" onClick={() => void settings.refetch()}>Reload settings</Button></p>}
    {settings.data && draft && <>
      {tab === 'notifications' && <div className="notification-settings-layout"><aside className="page-stack"><div className="form-field"><label htmlFor="notification-filter">Find a notification</label><input id="notification-filter" type="search" value={filter} onChange={e => setFilter(e.target.value)} /></div><nav className="notification-list" aria-label="Notification types">{settings.data.notifications.filter(item => `${item.definition.label} ${item.definition.category}`.toLowerCase().includes(filter.toLowerCase())).map(item => <button type="button" key={item.definition.id} className="notification-list-item" aria-pressed={selectedId === item.definition.id} onClick={() => setSelectedId(item.definition.id)}><strong>{item.definition.label}</strong><small>{item.definition.category.toLowerCase()} · {draft.rules[item.definition.id].enabled ? 'Enabled' : 'Disabled'}</small></button>)}</nav></aside>{selected && <NotificationRuleEditor key={selectedId} item={selected} rule={draft.rules[selectedId]} methods={settings.data.methods.map(value => ({ ...value, enabled: draft.methods[value.method.id] }))} disabled={saving} onChange={rule => change({ ...draft, rules: { ...draft.rules, [selectedId]: rule } })} />}</div>}
      {tab === 'methods' && <NotificationMethods methods={settings.data.methods} enabled={draft.methods} disabled={saving} onChange={(id, enabled) => change({ ...draft, methods: { ...draft.methods, [id]: enabled } })} />}
      {tab === 'history' && <NotificationHistory />}
      {tab === 'send' && <>{!settings.data.notifications.find(item => item.definition.id === 'general.message')?.rule.enabled && <p role="status">Enable General messages before sending from this page.</p>}<NotificationSendPanel disabled={dirty || saving || !settings.data.notifications.find(item => item.definition.id === 'general.message')?.rule.enabled} methods={settings.data.methods.filter(value => settings.data.notifications.find(item => item.definition.id === 'general.message')?.rule.allowedMethods.includes(value.method.id))} /></>}
      {error && <p role="alert">{error}</p>}{notice && <p role="status">{notice}</p>}
      <div className="save-bar"><span>{dirty ? 'You have unsaved changes' : 'Settings are up to date'}</span><div className="inline-actions"><Button variant="ghost" disabled={!dirty || saving} onClick={() => { setDraft(notificationInput(settings.data)); setDirty(false); setError(''); setNotice('') }}>Discard changes</Button><Button loading={saving} disabled={!dirty} onClick={() => void save()}>Save notification settings</Button></div></div>
    </>}
  </div>
}
