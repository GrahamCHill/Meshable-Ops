import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { dashboardEmbedsApi, type DashboardEmbedConfig } from '../../api/client'
import { useAuthStore } from '../../hooks/useAuthStore'
import { Button, Card, Spinner } from '../ui'
import { DashboardEmbed } from '../ui/DashboardEmbed'
import { Check, Info, LayoutDashboard, Pencil, Plus, Shield, Trash2, X } from 'lucide-react'

type DashboardFormValues = {
  label: string
  description: string
  dashboardId: string
  guestTokenResourceId: string
  sortOrder: string
  enabled: boolean
  allowedRoles: string[]
}

const emptyForm: DashboardFormValues = {
  label: '',
  description: '',
  dashboardId: '',
  guestTokenResourceId: '',
  sortOrder: '0',
  enabled: true,
  allowedRoles: [],
}

function DashboardConfigForm({
  initial,
  availableRoles,
  onSubmit,
  onCancel,
  loading,
}: {
  initial?: DashboardEmbedConfig
  availableRoles: string[]
  onSubmit: (data: DashboardFormValues) => void
  onCancel: () => void
  loading?: boolean
}) {
  const [form, setForm] = useState<DashboardFormValues>(() => initial ? {
    label: initial.label,
    description: initial.description ?? '',
    dashboardId: initial.dashboardId,
    guestTokenResourceId: initial.guestTokenResourceId ?? '',
    sortOrder: String(initial.sortOrder),
    enabled: initial.enabled,
    allowedRoles: initial.allowedRoles,
  } : emptyForm)

  useEffect(() => {
    setForm(initial ? {
      label: initial.label,
      description: initial.description ?? '',
      dashboardId: initial.dashboardId,
      guestTokenResourceId: initial.guestTokenResourceId ?? '',
      sortOrder: String(initial.sortOrder),
      enabled: initial.enabled,
      allowedRoles: initial.allowedRoles,
    } : emptyForm)
  }, [initial])

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
      <input
        placeholder="Display label"
        value={form.label}
        onChange={(e) => setForm(current => ({ ...current, label: e.target.value }))}
      />
      <textarea
        placeholder="Description"
        rows={2}
        value={form.description}
        onChange={(e) => setForm(current => ({ ...current, description: e.target.value }))}
      />
      <input
        placeholder="Embed UUID"
        value={form.dashboardId}
        onChange={(e) => setForm(current => ({ ...current, dashboardId: e.target.value }))}
      />
      <input
        placeholder="Numeric dashboard id for guest token (optional)"
        value={form.guestTokenResourceId}
        onChange={(e) => setForm(current => ({ ...current, guestTokenResourceId: e.target.value }))}
      />
      <input
        type="number"
        placeholder="Sort order"
        value={form.sortOrder}
        onChange={(e) => setForm(current => ({ ...current, sortOrder: e.target.value }))}
      />
      <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: '0.875rem', color: 'var(--color-text-secondary)' }}>
        <input
          type="checkbox"
          checked={form.enabled}
          onChange={(e) => setForm(current => ({ ...current, enabled: e.target.checked }))}
        />
        Enabled for shared viewing
      </label>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        <div style={{ fontSize: '0.875rem', color: 'var(--color-text-secondary)' }}>Allowed roles</div>
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
          {availableRoles.map((role) => (
            <label key={role} style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: '0.875rem', color: 'var(--color-text-secondary)' }}>
              <input
                type="checkbox"
                checked={form.allowedRoles.includes(role)}
                onChange={(e) => {
                  setForm((current) => ({
                    ...current,
                    allowedRoles: e.target.checked
                      ? [...current.allowedRoles, role]
                      : current.allowedRoles.filter((value) => value !== role),
                  }))
                }}
              />
              {role}
            </label>
          ))}
        </div>
      </div>
      <div style={{ display: 'flex', gap: 8 }}>
        <Button
          loading={loading}
          onClick={() => onSubmit(form)}
        >
          <Check size={14} /> Save
        </Button>
        <Button variant="ghost" onClick={onCancel}>
          <X size={14} /> Cancel
        </Button>
      </div>
    </div>
  )
}

export function DashboardsPage() {
  const queryClient = useQueryClient()
  const { user } = useAuthStore()
  const isAdmin = user?.role === 'ADMIN'
  const [active, setActive] = useState(0)
  const [showCreate, setShowCreate] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)

  const embedsQuery = useQuery({
    queryKey: ['dashboard-embeds'],
    queryFn: () => dashboardEmbedsApi.list(),
  })

  const adminEmbedsQuery = useQuery({
    queryKey: ['dashboard-embeds', 'admin'],
    queryFn: () => dashboardEmbedsApi.listAdmin(),
    enabled: isAdmin,
  })

  const rolesQuery = useQuery({
    queryKey: ['dashboard-embeds', 'roles'],
    queryFn: () => dashboardEmbedsApi.listRoles(),
    enabled: isAdmin,
  })

  const createMutation = useMutation({
    mutationFn: (data: DashboardFormValues) => dashboardEmbedsApi.create({
      label: data.label.trim(),
      description: data.description.trim() || undefined,
      dashboardId: data.dashboardId.trim(),
      guestTokenResourceId: data.guestTokenResourceId.trim() || undefined,
      sortOrder: Number(data.sortOrder || 0),
      enabled: data.enabled,
      allowedRoles: data.allowedRoles,
    }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['dashboard-embeds'] })
      queryClient.invalidateQueries({ queryKey: ['dashboard-embeds', 'admin'] })
      setShowCreate(false)
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: DashboardFormValues }) => dashboardEmbedsApi.update(id, {
      label: data.label.trim(),
      description: data.description.trim() || undefined,
      dashboardId: data.dashboardId.trim(),
      guestTokenResourceId: data.guestTokenResourceId.trim() || undefined,
      sortOrder: Number(data.sortOrder || 0),
      enabled: data.enabled,
      allowedRoles: data.allowedRoles,
    }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['dashboard-embeds'] })
      queryClient.invalidateQueries({ queryKey: ['dashboard-embeds', 'admin'] })
      setEditingId(null)
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => dashboardEmbedsApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['dashboard-embeds'] })
      queryClient.invalidateQueries({ queryKey: ['dashboard-embeds', 'admin'] })
      setEditingId(null)
    },
  })

  const dashboards = embedsQuery.data ?? []
  const adminDashboards = adminEmbedsQuery.data ?? dashboards
  const availableRoles = rolesQuery.data ?? []
  const current = dashboards[active]

  useEffect(() => {
    if (active > Math.max(dashboards.length - 1, 0)) {
      setActive(0)
    }
  }, [active, dashboards.length])

  return (
    <div style={{ width: '100%', maxWidth: 'none' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '1rem', marginBottom: '1.25rem', flexWrap: 'wrap' }}>
        <div>
          <h1 style={{ fontSize: '1.4rem', fontWeight: 700, marginBottom: 4 }}>Dashboards</h1>
          <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.875rem' }}>
            Embedded Apache Superset dashboards for authenticated app users
          </p>
        </div>
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: 6,
          fontSize: '0.8rem',
          color: 'var(--color-text-secondary)',
          padding: '6px 12px',
          border: '1px solid var(--color-border)',
          borderRadius: 'var(--radius-sm)',
          background: 'var(--color-surface)',
        }}>
          <Shield size={13} /> Dashboard embed list is now runtime-managed
        </div>
      </div>

      {isAdmin && (
        <Card style={{ marginBottom: '1rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'flex-start', marginBottom: '1rem', flexWrap: 'wrap' }}>
            <div>
              <div style={{ fontWeight: 600, marginBottom: 4 }}>Manage shared embeds</div>
              <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.85rem' }}>
                Add, remove, reorder, or disable shared dashboard embeds without rebuilding the frontend.
              </div>
            </div>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              <Button variant="secondary" onClick={() => setShowCreate(true)}>
                <Plus size={14} /> Add embed
              </Button>
              <Link to="/admin/superset" style={{ color: 'var(--color-accent)', textDecoration: 'none', fontWeight: 600, alignSelf: 'center' }}>
                Open /admin/superset
              </Link>
            </div>
          </div>

          {showCreate && (
            <Card style={{ marginBottom: '1rem', padding: '1rem' }}>
              <DashboardConfigForm
                availableRoles={availableRoles}
                onSubmit={(data) => createMutation.mutate(data)}
                onCancel={() => setShowCreate(false)}
                loading={createMutation.isPending}
              />
            </Card>
          )}

          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
            {adminDashboards.map((dashboard) => (
              <Card key={dashboard.id} style={{ padding: '1rem' }}>
                {editingId === dashboard.id ? (
                  <DashboardConfigForm
                    initial={dashboard}
                    availableRoles={availableRoles}
                    onSubmit={(data) => updateMutation.mutate({ id: dashboard.id, data })}
                    onCancel={() => setEditingId(null)}
                    loading={updateMutation.isPending}
                  />
                ) : (
                  <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'flex-start' }}>
                    <div style={{ flex: 1 }}>
                      <div style={{ fontWeight: 600, marginBottom: 4 }}>{dashboard.label}</div>
                      {dashboard.description && (
                        <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.85rem', marginBottom: 8 }}>
                          {dashboard.description}
                        </div>
                      )}
                      <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.8rem', lineHeight: 1.7 }}>
                        UUID: <code>{dashboard.dashboardId}</code><br />
                        Guest token resource: <code>{dashboard.guestTokenResourceId ?? dashboard.dashboardId}</code><br />
                        Sort order: <code>{dashboard.sortOrder}</code><br />
                        Status: <code>{dashboard.enabled ? 'enabled' : 'disabled'}</code><br />
                        Allowed roles: <code>{dashboard.allowedRoles.join(', ') || 'all authenticated roles'}</code>
                      </div>
                    </div>
                    <div style={{ display: 'flex', gap: 4, flexShrink: 0 }}>
                      <Button variant="ghost" size="sm" onClick={() => setEditingId(dashboard.id)}>
                        <Pencil size={13} />
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => deleteMutation.mutate(dashboard.id)}
                        disabled={deleteMutation.isPending}
                      >
                        <Trash2 size={13} style={{ color: 'var(--color-danger)' }} />
                      </Button>
                    </div>
                  </div>
                )}
              </Card>
            ))}
            {isAdmin && adminDashboards.length === 0 && (
              <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.875rem' }}>
                No dashboard embeds configured yet.
              </div>
            )}
          </div>
        </Card>
      )}

      {embedsQuery.isLoading && (
        <div style={{ display: 'flex', justifyContent: 'center', padding: '3rem' }}>
          <Spinner />
        </div>
      )}

      {!embedsQuery.isLoading && dashboards.length > 1 && (
        <div style={{
          display: 'flex', gap: 4, marginBottom: '1rem',
          borderBottom: '1px solid var(--color-border)', paddingBottom: 0,
          flexWrap: 'wrap',
        }}>
          {dashboards.map((dashboard, index) => (
            <button key={dashboard.id} onClick={() => setActive(index)} style={{
              padding: '8px 16px',
              fontSize: '0.875rem',
              fontWeight: active === index ? 600 : 400,
              color: active === index ? 'var(--color-text)' : 'var(--color-text-secondary)',
              background: 'none',
              border: 'none',
              borderBottom: active === index ? '2px solid var(--color-accent)' : '2px solid transparent',
              cursor: 'pointer',
              transition: 'all 150ms ease',
              marginBottom: -1,
            }}>
              {dashboard.label}
            </button>
          ))}
        </div>
      )}

      {!embedsQuery.isLoading && !current && (
        <div style={{
          background: 'var(--color-surface)',
          border: '1px solid var(--color-border)',
          borderRadius: 'var(--radius-lg)',
          padding: '3rem 2rem',
          textAlign: 'center',
        }}>
          <div style={{
            width: 48, height: 48, borderRadius: 12,
            background: 'var(--color-accent-muted)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            margin: '0 auto 1rem',
          }}>
            <LayoutDashboard size={22} color="var(--color-accent)" />
          </div>
          <h2 style={{ fontSize: '1.1rem', fontWeight: 600, marginBottom: 8 }}>No dashboard embeds configured</h2>
          <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.875rem', maxWidth: 520, margin: '0 auto 1.5rem' }}>
            Admins can add shared Superset embeds at runtime on this page without rebuilding the frontend.
          </p>
          <div style={{
            background: 'var(--color-bg)',
            border: '1px solid var(--color-border)',
            borderRadius: 'var(--radius-md)',
            padding: '1rem 1.5rem',
            textAlign: 'left',
            maxWidth: 560,
            margin: '0 auto',
            fontSize: '0.8rem',
            color: 'var(--color-text-secondary)',
            lineHeight: 1.8,
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 8, color: 'var(--color-text)', fontWeight: 500 }}>
              <Info size={14} /> Runtime setup
            </div>
            1. Create or edit dashboards in Superset.<br />
            2. Enable embedding and copy the embed UUID.<br />
            3. Use the admin panel on this page to add the shared embed record.<br />
            4. Any authenticated user can view enabled shared embeds here.
          </div>
        </div>
      )}

      {current && (
        <>
          {current.description && (
            <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.875rem', marginBottom: '0.75rem' }}>
              {current.description}
            </p>
          )}
          <DashboardEmbed
            key={current.id}
            dashboardId={current.dashboardId}
            guestTokenResourceId={current.guestTokenResourceId}
            height="calc(100vh - 12rem)"
          />
        </>
      )}
    </div>
  )
}
