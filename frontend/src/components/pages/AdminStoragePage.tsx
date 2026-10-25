import { useSearchParams } from 'react-router-dom'
import { Badge } from '../ui'
import { AdminPageHeader } from '../admin/AdminPageHeader'
import { StorageConnectionsPanel } from '../admin/storage/StorageConnectionsPanel'
import { StorageSettingsPanel } from '../admin/storage/StorageSettingsPanel'
import { GeneratedPdfCleanupSettings } from '../admin/storage/GeneratedPdfCleanupSettings'
import type { StorageSettingsTab } from '../admin/storage/useStorageSettings'

export function AdminStoragePage() {
  const [params, setParams] = useSearchParams()
  const requested = params.get('tab')
  const tab: StorageSettingsTab = requested === 'connection' || requested === 'vectors' || requested === 'cleanup' ? requested : 'shared'
  const setTab = (next: StorageSettingsTab) => setParams(next === 'shared' ? {} : { tab: next })
  return (
    <div className="page-stack">
      <AdminPageHeader title="Storage connections" description="Manage named buckets and containers, select accessible folders, and maintain the search index." actions={<Badge color="accent">Super admin · ADMIN</Badge>} />
      <nav className="section-tabs" aria-label="Storage settings">
        <button className={tab === 'shared' ? 'is-active' : ''} aria-current={tab === 'shared' ? 'page' : undefined} onClick={() => setTab('shared')}>Shared locations</button>
        <button className={tab === 'connection' ? 'is-active' : ''} aria-current={tab === 'connection' ? 'page' : undefined} onClick={() => setTab('connection')}>Default connection</button>
        <button className={tab === 'vectors' ? 'is-active' : ''} aria-current={tab === 'vectors' ? 'page' : undefined} onClick={() => setTab('vectors')}>Search index</button>
        <button className={tab === 'cleanup' ? 'is-active' : ''} aria-current={tab === 'cleanup' ? 'page' : undefined} onClick={() => setTab('cleanup')}>PDF cleanup</button>
      </nav>
      {tab === 'shared' && <StorageConnectionsPanel />}
      {tab === 'cleanup' && <GeneratedPdfCleanupSettings />}
      {/* Keep the settings draft mounted while viewing shared locations. */}
      <StorageSettingsPanel tab={tab} />
    </div>
  )
}
