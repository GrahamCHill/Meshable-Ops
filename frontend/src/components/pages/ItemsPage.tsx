import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { itemsApi, type Item, type StorageContents } from '../../api/client'
import { storageConnectionsApi } from '../../api/storageConnections'
import { useAuthStore } from '../../hooks/useAuthStore'
import { Badge, Button, Card, Spinner } from '../ui'

function FolderForm({
  initial,
  onSubmit,
  onCancel,
  loading,
}: Readonly<{
  initial?: Partial<Item>
  onSubmit: (data: { title: string; description?: string; tags?: string[] }) => void
  onCancel: () => void
  loading?: boolean
}>) {
  const [title, setTitle] = useState(initial?.title ?? '')
  const [description, setDescription] = useState(initial?.description ?? '')
  const [tagsRaw, setTagsRaw] = useState(initial?.tags?.join(', ') ?? '')

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
      <input placeholder="Storage root name *" value={title} onChange={e => setTitle(e.target.value)} />
      <textarea placeholder="Description" value={description} onChange={e => setDescription(e.target.value)} rows={3} />
      <input placeholder="Tags (comma separated)" value={tagsRaw} onChange={e => setTagsRaw(e.target.value)} />
      <div style={{ display: 'flex', gap: 8 }}>
        <Button
          loading={loading}
          onClick={() => onSubmit({
            title,
            description,
            tags: tagsRaw ? tagsRaw.split(',').map(tag => tag.trim()) : [],
          })}
        >
           Save
        </Button>
        <Button variant="ghost" onClick={onCancel}> Cancel</Button>
      </div>
    </div>
  )
}

function formatSize(size?: number) {
  if (size == null) return '—'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / (1024 * 1024)).toFixed(1)} MB`
}

function StorageBrowser({ folder, onClose }: Readonly<{ folder: Item; onClose: () => void }>) {
  const qc = useQueryClient()
  const { user } = useAuthStore()
  const [path, setPath] = useState('')
  const [transferError, setTransferError] = useState('')
  const fileInput = useRef<HTMLInputElement | null>(null)
  const folderInput = useRef<HTMLInputElement | null>(null)
  const contents = useQuery({
    queryKey: ['storage-contents', folder.id, path],
    queryFn: () => itemsApi.contents(folder.id, path),
  })
  const current = contents.isError ? undefined : contents.data
  const segments = (current?.path ?? path).split('/').filter(Boolean)
  const parentPath = segments.length > 1 ? `${segments.slice(0, -1).join('/')}/` : ''
  const upload = useMutation({
    mutationFn: (files: File[]) => itemsApi.upload(folder.id, path, files),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['storage-contents', folder.id] })
      qc.invalidateQueries({ queryKey: ['items'] })
      setTransferError('')
    },
    onError: () => setTransferError('Upload failed. Check your permission, file size, and storage settings.'),
  })
  const download = useMutation({
    mutationFn: ({ objectPath, archive }: { objectPath: string; archive: boolean; filename: string }) => itemsApi.download(folder.id, objectPath, archive),
    onSuccess: (blob, variables) => {
      const url = window.URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = variables.filename
      anchor.click()
      window.URL.revokeObjectURL(url)
      setTransferError('')
    },
    onError: () => setTransferError('Download failed. The object may have moved or storage may be unavailable.'),
  })

  const selectFiles = (files: FileList | null) => {
    if (files?.length) upload.mutate(Array.from(files))
  }

  const navigateBreadcrumb = (index: number) => {
    if (index < 0) setPath('')
    else setPath(`${segments.slice(0, index + 1).join('/')}/`)
  }

  return (
    <div style={{ maxWidth: 900 }}>
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 12, marginBottom: '1rem', flexWrap: 'wrap' }}>
        <div>
          <Button variant="ghost" size="sm" onClick={onClose}> {folder.connectionNickname ?? 'Storage'} folders</Button>
          <h1 style={{ fontSize: '1.4rem', fontWeight: 700, marginTop: 12 }}>{folder.title}</h1>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 6 }}>
            <Badge color="success">{current?.storageProvider ?? folder.storageProvider}</Badge>
            <span style={{ fontFamily: 'monospace', fontSize: '0.75rem', color: 'var(--color-text-tertiary)' }}>
              {folder.connectionNickname ? `${folder.connectionNickname}/${folder.title}/` : folder.storagePrefix}{current?.path ?? path}
            </span>
          </div>
        </div>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          <Button variant="secondary" size="sm" loading={download.isPending} onClick={() => download.mutate({ objectPath: path, archive: true, filename: `${segments[segments.length - 1] ?? folder.title}.zip` })}> Download folder</Button>
          {user?.canUploadDocuments && <>
            <Button variant="secondary" size="sm" loading={upload.isPending} onClick={() => fileInput.current?.click()}> Upload files</Button>
            <Button size="sm" loading={upload.isPending} onClick={() => folderInput.current?.click()}> Upload folder</Button>
            <input ref={fileInput} type="file" multiple hidden onChange={event => { selectFiles(event.target.files); event.target.value = '' }} />
            <input ref={element => { folderInput.current = element; element?.setAttribute('webkitdirectory', '') }} type="file" multiple hidden onChange={event => { selectFiles(event.target.files); event.target.value = '' }} />
          </>}
        </div>
      </div>

      {transferError && <div style={{ color: 'var(--color-danger)', fontSize: '0.82rem', marginBottom: '0.75rem' }}>{transferError}</div>}

      <Card style={{ padding: 0, overflow: 'hidden' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '0.85rem 1rem',  flexWrap: 'wrap' }}>
          <button onClick={() => navigateBreadcrumb(-1)} style={breadcrumbStyle}> {folder.title}</button>
          {segments.map((segment, index) => (
            <span key={`${segment}-${index}`} style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
              <span aria-hidden="true">/</span>
              <button onClick={() => navigateBreadcrumb(index)} style={breadcrumbStyle}>{segment}</button>
            </span>
          ))}
        </div>

        {contents.isLoading && <div style={{ display: 'flex', justifyContent: 'center', padding: '3rem' }}><Spinner /></div>}
        {contents.isError && (
          <div style={{ padding: '1.25rem', color: 'var(--color-danger)' }}>
            Could not load this storage path. Check the provider settings and credentials.
          </div>
        )}
        {current && <StorageEntries contents={current} parentPath={parentPath} onNavigate={setPath} onDownload={(objectPath, archive, filename) => download.mutate({ objectPath, archive, filename })} downloading={download.isPending} />}
      </Card>

      <Card style={{ marginTop: '1rem', background: 'var(--color-surface-2)' }}>
        <div style={{ fontSize: '0.8rem', color: 'var(--color-text-secondary)' }}>
          Browse and download files within this shared folder. Folder and document metadata are searchable.
        </div>
      </Card>
    </div>
  )
}

function StorageEntries({
  contents,
  parentPath,
  onNavigate,
  onDownload,
  downloading,
}: Readonly<{
  contents: StorageContents
  parentPath: string
  onNavigate: (path: string) => void
  onDownload: (path: string, archive: boolean, filename: string) => void
  downloading: boolean
}>) {
  return (
    <div>
      {contents.path && (
        <button onClick={() => onNavigate(parentPath)} style={entryStyle}>

          <span style={{ flex: 1, textAlign: 'left' }}>..</span>
        </button>
      )}
      {contents.entries.map(entry => {
        const vectorLabel = entry.vectorStatus === 'VECTORIZED' ? ' · Vector' : ''
        const detail = entry.type === 'FOLDER' ? 'Folder' : `${formatSize(entry.size)}${vectorLabel}`
        return (
          <div key={`${entry.type}-${entry.path}`} style={entryStyle}>

            <span style={{ flex: 1, minWidth: 0, textAlign: 'left', overflow: 'hidden', textOverflow: 'ellipsis' }}>{entry.name}</span>
            <span style={{ width: 90, textAlign: 'right', color: 'var(--color-text-tertiary)', fontSize: '0.75rem' }}>
              {detail}
            </span>
            {entry.type === 'FOLDER' && <Button variant="ghost" size="sm" onClick={() => onNavigate(entry.path)}>Open </Button>}
            <Button variant="ghost" size="sm" disabled={downloading} onClick={() => onDownload(entry.path, entry.type === 'FOLDER', entry.type === 'FOLDER' ? `${entry.name}.zip` : entry.name)} title={`Download ${entry.name}`}>Download</Button>
          </div>
        )
      })}
      {contents.entries.length === 0 && (
        <div style={{ textAlign: 'center', padding: '3rem', color: 'var(--color-text-secondary)' }}>This folder is empty.</div>
      )}
    </div>
  )
}

const breadcrumbStyle: React.CSSProperties = {
  display: 'inline-flex', alignItems: 'center', gap: 5,  background: 'transparent',
  color: 'var(--color-text-secondary)', cursor: 'pointer', padding: 3, fontWeight: 500,
}

const entryStyle: React.CSSProperties = {
  width: '100%', display: 'flex', alignItems: 'center', gap: 10, padding: '0.8rem 1rem',
    background: 'transparent', color: 'var(--color-text)',
}

export function ItemsPage() {
  const qc = useQueryClient()
  const { user } = useAuthStore()
  const isAdmin = user?.role === 'ADMIN'
  const [showCreate, setShowCreate] = useState(false)
  const [editing, setEditing] = useState<string | null>(null)
  const [selected, setSelected] = useState<Item | null>(null)
  const [params, setParams] = useSearchParams()
  const connectionId = params.get('connection') ?? undefined
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
