import { useAccess, isAdminRole } from '../../api/access'
import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { itemsApi, type Item } from '../../api/items'
import { storageConnectionsApi } from '../../api/storageConnections'
import { useAuthStore } from '../../hooks/useAuthStore'
import { Badge, Button, Card, Spinner } from '../ui'
import { FolderForm } from '../storage/FolderForm'
import { StorageBrowser } from '../storage/StorageBrowser'
import { StorageSearch } from '../search/StorageSearch'

export function ItemsPage() {
  const qc = useQueryClient()
  const { user } = useAuthStore()
  const access = useAccess()
  const isAdmin = isAdminRole(user?.role) && access.can('admin.storage')
  const [showCreate, setShowCreate] = useState(false)
  const [editing, setEditing] = useState<string | null>(null)
  const [selected, setSelected] = useState<Item | null>(null)
  const [params, setParams] = useSearchParams()
  const connectionId = params.get('connection') ?? undefined
  const linkedFolderId = params.get('folder')
  const linkedPath = params.get('path') ?? ''
  const linkedFile = params.get('file') ?? undefined
  const linkedFolder = useQuery({ queryKey: ['storage-folder', linkedFolderId], queryFn: () => itemsApi.get(linkedFolderId!), enabled: !!linkedFolderId })
  const closeLinkedFolder = () => setParams(previous => { const next = new URLSearchParams(previous); next.delete('folder'); next.delete('path'); next.delete('file'); return next })
  const [page, setPage] = useState(0)
  useEffect(() => { setSelected(null); setPage(0); setEditing(null); setShowCreate(false) }, [connectionId])
  const locations = useQuery({ queryKey: ['storage-connections'], queryFn: storageConnectionsApi.list })
  const connection = locations.data?.find(location => location.id === connectionId)
  const selectConnection = (id?: string) => {
    setPage(0); setSelected(null); setEditing(null); setShowCreate(false)
    setParams(previous => { const next = new URLSearchParams(previous); if (id) next.set('connection', id); else next.delete('connection'); return next })
  }

  const { data, isLoading, isError, refetch } = useQuery({ queryKey: ['items', connectionId, page], queryFn: () => itemsApi.list(page, 20, connectionId) })
  const create = useMutation({
    mutationFn: itemsApi.create,
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['items'] }); setShowCreate(false) },
  })
  const update = useMutation({
    mutationFn: ({ id, ...payload }: { id: string } & Parameters<typeof itemsApi.create>[0]) => itemsApi.update(id, payload),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['items'] }); setEditing(null) },
  })
  const del = useMutation({
    mutationFn: itemsApi.delete,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['items'] }),
  })

  if (linkedFolderId) {
    if (linkedFolder.isLoading) return <div role="status"><Spinner /> Loading shared folder…</div>
    if (linkedFolder.isError || !linkedFolder.data) return <div className="page-stack"><p role="alert">This folder is unavailable or no longer shared.</p><Button variant="ghost" onClick={closeLinkedFolder}>Back to Storage</Button></div>
    return <StorageBrowser key={`${linkedFolderId}-${linkedPath}-${linkedFile}`} folder={linkedFolder.data} initialPath={linkedPath} initialFile={linkedFile} onClose={closeLinkedFolder} />
  }
  if (selected) return <StorageBrowser folder={selected} onClose={() => setSelected(null)} />

  return (
    <div style={{ maxWidth: 1100 }}>
      {connectionId && <Button variant="ghost" size="sm" onClick={() => selectConnection()} style={{ marginBottom: 12 }}> All storage locations</Button>}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem', gap: 12 }}>
        <div>
          <h1 style={{ fontSize: '1.4rem', fontWeight: 700 }}>{connectionId ? connection?.nickname ?? 'Storage location' : 'Storage'}</h1>
          <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.875rem' }}>
            {connectionId ? 'Folders shared from this location' : 'Your shared cloud locations and document folders'}
          </p>
          {!isAdmin && <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.8rem', marginTop: 6 }}>Your administrator chooses which folders are accessible.</p>}
        </div>
        {isAdmin && <div className="inline-actions"><Link className="button button-secondary button-sm" to="/admin/storage">Manage shared locations</Link>{!connectionId && <Button onClick={() => setShowCreate(true)}> New storage root</Button>}</div>}
      </div>

      {isAdmin && showCreate && <Card style={{ marginBottom: '1rem' }}><FolderForm onSubmit={create.mutate} onCancel={() => setShowCreate(false)} loading={create.isPending} /></Card>}
      <StorageSearch />
      {!connectionId && <section className="page-stack" aria-label="Cloud storage locations" style={{ marginBottom: 24 }}>
        {locations.isLoading && <div role="status"><Spinner /> Loading locations…</div>}
        {locations.isError && <div role="alert" className="feedback error">Unable to load shared locations. <Button variant="ghost" size="sm" onClick={() => locations.refetch()}>Retry</Button></div>}
        {!!locations.data?.length && <><h2 className="card-title">Cloud locations</h2><div className="storage-location-grid">{locations.data.map(location => <button className="storage-location-card" key={location.id} onClick={() => selectConnection(location.id)}><span><strong>{location.nickname}</strong><small>{location.provider === 'S3' ? 'S3' : 'Azure Blob'} · {location.folderCount} shared {location.folderCount === 1 ? 'folder' : 'folders'}</small></span></button>)}</div><h2 className="card-title">Application-managed folders</h2></>}
      </section>}
      {isLoading && <div style={{ display: 'flex', justifyContent: 'center', padding: '3rem' }}><Spinner /></div>}
      {isError && <div role="alert" className="feedback error">Unable to load shared folders. <Button variant="ghost" size="sm" onClick={() => refetch()}>Retry</Button></div>}
      {(create.isError || update.isError || del.isError) && <p role="alert" className="feedback error">Unable to change this folder. Check that the storage connection is available and try again.</p>}

      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
        {data?.content.map(folder => (
          <Card key={folder.id}>
            {isAdmin && editing === folder.id ? (
              <FolderForm initial={folder} loading={update.isPending} onSubmit={payload => update.mutate({ id: folder.id, ...payload })} onCancel={() => setEditing(null)} />
            ) : (
              <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12 }}>
                <div style={{ display: 'flex', gap: 12, flex: 1, minWidth: 0 }}>

                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontWeight: 600, marginBottom: 4 }}>{folder.title}</div>
                    {folder.description && <p style={{ fontSize: '0.875rem', color: 'var(--color-text-secondary)', marginBottom: 8 }}>{folder.description}</p>}
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap', marginBottom: 8 }}>
                      <Badge color={folder.storageProvider === 'NONE' ? 'warning' : 'success'}>{folder.storageProvider}</Badge>
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, fontSize: '0.8rem', color: 'var(--color-text-secondary)' }}> {folder.connectionId ? 'Shared folder' : `${folder.documentCount} objects`}</span>
                    </div>
                    {folder.tags?.length ? <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>{folder.tags.map(tag => <Badge key={tag} color="accent">{tag}</Badge>)}</div> : null}
                    {!folder.connectionId && <div style={{ fontFamily: 'monospace', fontSize: '0.72rem', color: 'var(--color-text-tertiary)', marginTop: 8 }}>{folder.storagePrefix}</div>}
                  </div>
                </div>
                <div style={{ display: 'flex', gap: 4, alignItems: 'flex-start', flexShrink: 0 }}>
                  <Button variant="secondary" size="sm" onClick={() => setSelected(folder)}>Open </Button>
                  {isAdmin && <Button variant="ghost" size="sm" onClick={() => setEditing(folder.id)}>Edit</Button>}
                  {isAdmin && <Button variant="ghost" size="sm" onClick={() => del.mutate(folder.id)}>Remove</Button>}
                </div>
              </div>
            )}
          </Card>
        ))}
        {data?.content.length === 0 && <div style={{ textAlign: 'center', padding: '2rem', color: 'var(--color-text-secondary)' }}>{connectionId ? 'No accessible folders in this location.' : 'No application-managed folders are shared.'}</div>}
      </div>
      {data && data.totalPages > 1 && <div className="inline-actions" style={{ marginTop: 16 }}><Button variant="secondary" size="sm" disabled={page === 0} onClick={() => setPage(value => value - 1)}>Previous</Button><span>Page {page + 1} of {data.totalPages}</span><Button variant="secondary" size="sm" disabled={page + 1 >= data.totalPages} onClick={() => setPage(value => value + 1)}>Next</Button></div>}
    </div>
  )
}
