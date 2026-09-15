import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { adminSettingsApi, itemsApi, type AdminSettings } from '../../api/client'
import { Button, Card, Spinner } from '../ui'
import { AdminPageHeader } from '../admin/AdminPageHeader'
import { formatDate, getErrorMessage } from '../admin/adminUtils'
import { HardDrive, RefreshCcw } from 'lucide-react'

type SettingsForm = {
  storageProvider: 'NONE' | 'S3' | 'AZURE_BLOB'
  storageBucket: string
  storageRegion: string
  storageEndpoint: string
  storageAccessKey: string
  storageSecretKey: string
  storageRootPath: string
  vectorReindexEnabled: boolean
  vectorReindexIntervalMinutes: number
}

const emptyForm: SettingsForm = {
  storageProvider: 'NONE', storageBucket: '', storageRegion: '', storageEndpoint: '', storageAccessKey: '',
  storageSecretKey: '', storageRootPath: 'folders', vectorReindexEnabled: false, vectorReindexIntervalMinutes: 1440,
}

function toForm(settings: AdminSettings): SettingsForm {
  return {
    storageProvider: settings.storageProvider,
    storageBucket: settings.storageBucket ?? '',
    storageRegion: settings.storageRegion ?? '',
    storageEndpoint: settings.storageEndpoint ?? '',
    storageAccessKey: settings.storageAccessKey ?? '',
    storageSecretKey: settings.storageSecretKey ?? '',
    storageRootPath: settings.storageRootPath ?? 'folders',
    vectorReindexEnabled: settings.vectorReindexEnabled,
    vectorReindexIntervalMinutes: settings.vectorReindexIntervalMinutes,
  }
}

const labelStyle = { fontSize: '0.8rem', fontWeight: 500, display: 'block', marginBottom: 4 } as const

export function AdminStoragePage() {
  const queryClient = useQueryClient()
  const [form, setForm] = useState(emptyForm)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const settings = useQuery({ queryKey: ['admin-settings'], queryFn: adminSettingsApi.get })

  useEffect(() => { if (settings.data) setForm(toForm(settings.data)) }, [settings.data])

  const save = useMutation({
    mutationFn: adminSettingsApi.update,
    onSuccess: saved => { queryClient.invalidateQueries({ queryKey: ['admin-settings'] }); setForm(toForm(saved)); setError(''); setNotice(`Saved ${formatDate(saved.updatedAt)}`) },
    onError: err => { setNotice(''); setError(getErrorMessage(err, 'Unable to save admin settings right now.')) },
  })
  const reindex = useMutation({
    mutationFn: itemsApi.reindex,
    onSuccess: result => { queryClient.invalidateQueries({ queryKey: ['admin-settings'] }); setNotice(result.message); setError('') },
    onError: err => { setNotice(''); setError(getErrorMessage(err, 'Unable to recalculate vectors right now.')) },
  })

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
      <AdminPageHeader title="Storage & vectors" description="Set the application-visible S3 or Azure Blob boundary and manage document-level vector maintenance." icon={HardDrive} />
      {settings.isLoading ? <Card style={{ padding: '3rem', textAlign: 'center' }}><Spinner /></Card> : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '1rem', alignItems: 'start' }}>
          <Card>
            <div style={{ fontWeight: 600, marginBottom: 4 }}>Object storage</div>
            <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.8rem', marginBottom: '1rem' }}>Credentials may be left blank when workload identity is configured.</div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
              <div><label style={labelStyle}>Provider</label><select value={form.storageProvider} onChange={e => setForm({ ...form, storageProvider: e.target.value as SettingsForm['storageProvider'] })}><option value="NONE">NONE</option><option value="S3">S3</option><option value="AZURE_BLOB">AZURE_BLOB</option></select></div>
              <div><label style={labelStyle}>Bucket / container</label><input placeholder="contract-documents" value={form.storageBucket} onChange={e => setForm({ ...form, storageBucket: e.target.value })} /></div>
              <div><label style={labelStyle}>Storage view path</label><input placeholder="contract-management/documents" value={form.storageRootPath} onChange={e => setForm({ ...form, storageRootPath: e.target.value })} /><div style={{ marginTop: 5, color: 'var(--color-text-tertiary)', fontSize: '0.75rem' }}>Only objects beneath this persistent prefix are exposed in Storage.</div></div>
              <div><label style={labelStyle}>Endpoint</label><input placeholder={form.storageProvider === 'AZURE_BLOB' ? 'https://account.blob.core.windows.net' : 'https://s3.amazonaws.com'} value={form.storageEndpoint} onChange={e => setForm({ ...form, storageEndpoint: e.target.value })} /></div>
              <div><label style={labelStyle}>Region (S3 only)</label><input disabled={form.storageProvider === 'AZURE_BLOB'} placeholder="us-east-1" value={form.storageRegion} onChange={e => setForm({ ...form, storageRegion: e.target.value })} /></div>
              <div><label style={labelStyle}>{form.storageProvider === 'AZURE_BLOB' ? 'Storage account name' : 'Access key'}</label><input value={form.storageAccessKey} onChange={e => setForm({ ...form, storageAccessKey: e.target.value })} /></div>
              <div><label style={labelStyle}>{form.storageProvider === 'AZURE_BLOB' ? 'Storage account key' : 'Secret key'}</label><input type="password" value={form.storageSecretKey} onChange={e => setForm({ ...form, storageSecretKey: e.target.value })} /></div>
            </div>
          </Card>
          <Card>
            <div style={{ fontWeight: 600, marginBottom: 4 }}>Vector maintenance</div>
            <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.8rem', marginBottom: '1rem' }}>Controls existing metadata vectorization. Text extraction, OCR, image classification, and richer strategies can be added behind the document processing boundary later.</div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.9rem' }}>
              <label style={{ display: 'inline-flex', alignItems: 'center', gap: 8, fontSize: '0.85rem' }}><input type="checkbox" checked={form.vectorReindexEnabled} onChange={e => setForm({ ...form, vectorReindexEnabled: e.target.checked })} /> Enable automatic vector recalculation</label>
              <div><label style={labelStyle}>Reindex interval (minutes)</label><input type="number" min={15} max={10080} value={form.vectorReindexIntervalMinutes} onChange={e => setForm({ ...form, vectorReindexIntervalMinutes: Number(e.target.value) || 15 })} /></div>
              <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.8rem', lineHeight: 1.6 }}>Last vector run: {formatDate(settings.data?.lastVectorReindexAt)}<br />Status: {settings.data?.lastVectorReindexStatus ?? 'No runs recorded yet'}</div>
              <Button variant="secondary" loading={reindex.isPending} onClick={() => reindex.mutate()}><RefreshCcw size={14} /> Recalculate vectors now</Button>
            </div>
          </Card>
        </div>
      )}
      {error && <div style={{ color: 'var(--color-danger)', fontSize: '0.82rem' }}>{error}</div>}
      {notice && <div style={{ color: 'var(--color-success)', fontSize: '0.82rem' }}>{notice}</div>}
      {!settings.isLoading && <Button loading={save.isPending} onClick={() => save.mutate(form)} style={{ alignSelf: 'flex-start', minWidth: 160 }}>Save settings</Button>}
    </div>
  )
}
