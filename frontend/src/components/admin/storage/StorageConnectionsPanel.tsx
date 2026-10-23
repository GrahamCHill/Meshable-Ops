import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { storageConnectionsApi, type StorageConnection } from '../../../api/storageConnections'
import { Button, Card, Spinner } from '../../ui'
import { ConnectionForm } from './ConnectionForm'
import { ConnectionCard } from './ConnectionCard'
import { FolderAccessRow } from './FolderAccessRow'

export function StorageConnectionsPanel() {
  const [editing, setEditing] = useState<StorageConnection | 'new' | null>(null)
  const connections = useQuery({ queryKey: ['admin-storage-connections'], queryFn: storageConnectionsApi.listAdmin })
  const legacy = useQuery({ queryKey: ['admin-storage-folders'], queryFn: storageConnectionsApi.legacyFolders })
  return <div className="page-stack">
    <div className="page-heading"><div><h2 className="card-title">Shared storage locations</h2><p className="field-help" style={{ margin: 0 }}>Add multiple S3 buckets or Azure containers, then select exactly which folders people can access.</p></div><Button onClick={() => setEditing('new')}> Add connection</Button></div>
    {editing && <ConnectionForm key={editing === 'new' ? 'new' : editing.id} initial={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)} />}
    {connections.isLoading && <Card><Spinner /> Loading connections…</Card>}
    {connections.isError && <Card><p role="alert" className="feedback error">Unable to load connections.</p><Button variant="secondary" onClick={() => connections.refetch()}>Retry</Button></Card>}
    {connections.data?.map(connection => <ConnectionCard key={connection.id} connection={connection} onEdit={() => setEditing(connection)} />)}
    {connections.data?.length === 0 && !editing && <Card><div className="empty-state"><h2>Connect your first shared location</h2><p>Give each bucket or container a familiar name. Its selected folders will appear beneath it in Storage.</p><Button onClick={() => setEditing('new')}> Add connection</Button></div></Card>}
    {!!legacy.data?.length && <Card><h2 className="card-title">Application-managed folders</h2><p className="field-help">Control visibility and nicknames for folders on the default connection. Hiding a folder also removes its contents from search results.</p>{legacy.data.map(folder => <FolderAccessRow key={folder.id} folder={folder} />)}</Card>}
    {legacy.isError && <p role="alert" className="feedback error">Unable to load application-managed folder access. <Button variant="ghost" size="sm" onClick={() => legacy.refetch()}>Retry</Button></p>}
  </div>
}
