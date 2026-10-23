import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'

import { dashboardEmbedsApi, type DashboardEmbedConfig, type SupersetDashboardOption } from '../../api/client'
import { Button, Card, Spinner } from '../ui'
import { AdminPageHeader } from '../admin/AdminPageHeader'

type FormValues = { label: string; description: string; supersetDashboardId: string; sortOrder: string; enabled: boolean; allowedRoles: string[] }
const emptyForm: FormValues = { label: '', description: '', supersetDashboardId: '', sortOrder: '0', enabled: true, allowedRoles: [] }

function LinkForm({ initial, dashboards, roles, loading, onSubmit, onCancel }: Readonly<{ initial?: DashboardEmbedConfig; dashboards: SupersetDashboardOption[]; roles: string[]; loading: boolean; onSubmit: (value: FormValues) => void; onCancel: () => void }>) {
  const initialValue = (): FormValues => initial ? { label: initial.label, description: initial.description ?? '', supersetDashboardId: initial.guestTokenResourceId ?? '', sortOrder: String(initial.sortOrder), enabled: initial.enabled, allowedRoles: initial.allowedRoles } : emptyForm
  const [form, setForm] = useState<FormValues>(initialValue)
  useEffect(() => setForm(initialValue()), [initial])
  return <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
    <select value={form.supersetDashboardId} onChange={e => { const selected = dashboards.find(d => d.id === Number(e.target.value)); setForm(current => ({ ...current, supersetDashboardId: e.target.value, label: current.label || selected?.title || '' })) }}>
      <option value="">Select a Superset dashboard…</option>
      {dashboards.map(d => <option key={d.id} value={d.id}>{d.title} (#{d.id}){d.embeddingEnabled ? ' · linked' : ''}</option>)}
    </select>
    <input placeholder="Display label" value={form.label} onChange={e => setForm(current => ({ ...current, label: e.target.value }))} />
    <textarea rows={2} placeholder="Description" value={form.description} onChange={e => setForm(current => ({ ...current, description: e.target.value }))} />
    <input type="number" placeholder="Sort order" value={form.sortOrder} onChange={e => setForm(current => ({ ...current, sortOrder: e.target.value }))} />
    <label style={labelStyle}><input type="checkbox" checked={form.enabled} onChange={e => setForm(current => ({ ...current, enabled: e.target.checked }))} /> Enabled for shared viewing</label>
    <div><div style={{ fontSize: '0.85rem', color: 'var(--color-text-secondary)', marginBottom: 8 }}>Allowed roles</div><div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>{roles.map(role => <label key={role} style={labelStyle}><input type="checkbox" checked={form.allowedRoles.includes(role)} onChange={e => setForm(current => ({ ...current, allowedRoles: e.target.checked ? [...current.allowedRoles, role] : current.allowedRoles.filter(value => value !== role) }))} /> {role}</label>)}</div></div>
    <div style={{ display: 'flex', gap: 8 }}><Button loading={loading} disabled={!form.supersetDashboardId || !form.label.trim()} onClick={() => onSubmit(form)}> Save link</Button><Button variant="ghost" onClick={onCancel}> Cancel</Button></div>
    <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.78rem' }}>Embedding is configured automatically when you save.</p>
  </div>
}

export function AdminDashboardLinksPage() {
  const qc = useQueryClient(); const [showCreate, setShowCreate] = useState(false); const [editingId, setEditingId] = useState<string | null>(null)
  const links = useQuery({ queryKey: ['dashboard-embeds', 'admin'], queryFn: dashboardEmbedsApi.listAdmin })
  const available = useQuery({ queryKey: ['superset-dashboards'], queryFn: dashboardEmbedsApi.listAvailable })
  const roles = useQuery({ queryKey: ['dashboard-embeds', 'roles'], queryFn: dashboardEmbedsApi.listRoles })
  const invalidate = () => { qc.invalidateQueries({ queryKey: ['dashboard-embeds'] }); qc.invalidateQueries({ queryKey: ['dashboard-embeds', 'admin'] }); qc.invalidateQueries({ queryKey: ['superset-dashboards'] }) }
  const payload = (d: FormValues) => ({ label: d.label.trim(), description: d.description.trim() || undefined, supersetDashboardId: Number(d.supersetDashboardId), sortOrder: Number(d.sortOrder || 0), enabled: d.enabled, allowedRoles: d.allowedRoles })
  const create = useMutation({ mutationFn: (d: FormValues) => dashboardEmbedsApi.create(payload(d)), onSuccess: () => { invalidate(); setShowCreate(false) } })
  const update = useMutation({ mutationFn: ({ id, data }: { id: string; data: FormValues }) => dashboardEmbedsApi.update(id, payload(data)), onSuccess: () => { invalidate(); setEditingId(null) } })
  const remove = useMutation({ mutationFn: dashboardEmbedsApi.delete, onSuccess: invalidate })
  const dashboards = available.data ?? []
  return <div style={{ maxWidth: 1100 }}>
    <AdminPageHeader title="Dashboard links" description="Choose which Superset dashboards appear in the app and who can view them."  actions={<div className="inline-actions"><Button variant="secondary" onClick={() => { available.refetch(); links.refetch() }}> Refresh Superset</Button><Button onClick={() => setShowCreate(true)}> Add link</Button></div>} />
    <Card style={{ margin: '1rem 0', background: 'var(--color-surface-2)' }}><div style={{ display: 'flex', gap: 10 }}><div style={{ fontSize: '0.84rem', color: 'var(--color-text-secondary)' }}>Create and publish dashboards in the <Link to="/admin/superset-graph-builder">Superset workspace</Link>, then select them here to share with your team.</div></div></Card>
    {(available.isLoading || links.isLoading) && <div style={{ padding: '2rem', textAlign: 'center' }}><Spinner /></div>}
    {(available.error || links.error) && <Card style={{ color: 'var(--color-danger)', marginBottom: '1rem' }}>Could not load dashboards. Check the Superset service-account credentials.</Card>}
    {showCreate && <Card style={{ marginBottom: '1rem' }}><LinkForm dashboards={dashboards} roles={roles.data ?? []} loading={create.isPending} onSubmit={d => create.mutate(d)} onCancel={() => setShowCreate(false)} /></Card>}
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>{(links.data ?? []).map(link => <Card key={link.id}>{editingId === link.id ? <LinkForm initial={link} dashboards={dashboards} roles={roles.data ?? []} loading={update.isPending} onSubmit={data => update.mutate({ id: link.id, data })} onCancel={() => setEditingId(null)} /> : <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12 }}><div><div style={{ fontWeight: 600 }}>{link.label}</div>{link.description && <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.84rem', marginTop: 4 }}>{link.description}</div>}<div style={{ color: 'var(--color-text-tertiary)', fontSize: '0.78rem', marginTop: 8 }}>Superset dashboard #{link.guestTokenResourceId} · {link.enabled ? 'enabled' : 'disabled'} · roles: {link.allowedRoles.join(', ') || 'all'}</div></div><div style={{ display: 'flex', gap: 4 }}><Button variant="ghost" size="sm" onClick={() => setEditingId(link.id)}>Edit</Button><Button variant="ghost" size="sm" onClick={() => remove.mutate(link.id)}>Remove</Button></div></div>}</Card>)}</div>
    {!links.isLoading && (links.data ?? []).length === 0 && <Card style={{ textAlign: 'center', color: 'var(--color-text-secondary)' }}>No dashboard links configured.</Card>}
    <Link to="/admin/superset-graph-builder" style={{ display: 'inline-flex', gap: 6, alignItems: 'center', marginTop: 16 }}> Open Superset workspace</Link>
  </div>
}

const labelStyle: React.CSSProperties = { display: 'flex', alignItems: 'center', gap: 6, fontSize: '0.875rem', color: 'var(--color-text-secondary)' }
