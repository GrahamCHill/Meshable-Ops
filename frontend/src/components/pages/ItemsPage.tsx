import { useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Check, ChevronRight, Download, File, Files, Folder, FolderOpen, Pencil, Plus, Trash2, Upload, X } from 'lucide-react'
import { itemsApi, type Item, type StorageContents } from '../../api/client'
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
          <Check size={14} /> Save
        </Button>
        <Button variant="ghost" onClick={onCancel}><X size={14} /> Cancel</Button>
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
  const current = contents.data
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
          <Button variant="ghost" size="sm" onClick={onClose}><ArrowLeft size={14} /> Storage roots</Button>
          <h1 style={{ fontSize: '1.4rem', fontWeight: 700, marginTop: 12 }}>{folder.title}</h1>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 6 }}>
            <Badge color="success">{current?.storageProvider ?? folder.storageProvider}</Badge>
            <span style={{ fontFamily: 'monospace', fontSize: '0.75rem', color: 'var(--color-text-tertiary)' }}>
              {folder.storagePrefix}{current?.path ?? path}
            </span>
          </div>
        </div>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          <Button variant="secondary" size="sm" loading={download.isPending} onClick={() => download.mutate({ objectPath: path, archive: true, filename: `${segments[segments.length - 1] ?? folder.title}.zip` })}><Download size={14} /> Download folder</Button>
          {user?.canUploadDocuments && <>
            <Button variant="secondary" size="sm" loading={upload.isPending} onClick={() => fileInput.current?.click()}><Upload size={14} /> Upload files</Button>
            <Button size="sm" loading={upload.isPending} onClick={() => folderInput.current?.click()}><FolderOpen size={14} /> Upload folder</Button>
            <input ref={fileInput} type="file" multiple hidden onChange={event => { selectFiles(event.target.files); event.target.value = '' }} />
            <input ref={element => { folderInput.current = element; element?.setAttribute('webkitdirectory', '') }} type="file" multiple hidden onChange={event => { selectFiles(event.target.files); event.target.value = '' }} />
          </>}
        </div>
      </div>

      {transferError && <div style={{ color: 'var(--color-danger)', fontSize: '0.82rem', marginBottom: '0.75rem' }}>{transferError}</div>}

      <Card style={{ padding: 0, overflow: 'hidden' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '0.85rem 1rem', borderBottom: '1px solid var(--color-border)', flexWrap: 'wrap' }}>
          <button onClick={() => navigateBreadcrumb(-1)} style={breadcrumbStyle}><FolderOpen size={15} /> {folder.title}</button>
          {segments.map((segment, index) => (
            <span key={`${segment}-${index}`} style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
              <ChevronRight size={14} color="var(--color-text-tertiary)" />
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
          This view reads child folders and objects directly from {current?.storageProvider ?? folder.storageProvider}.
          Document metadata is indexed in pgvector. Content extraction, image classification, OCR, and content-derived vectors remain planned but are not enabled.
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
          <Folder size={19} color="var(--color-accent)" />
          <span style={{ flex: 1, textAlign: 'left' }}>..</span>
        </button>
      )}
      {contents.entries.map(entry => {
        const vectorLabel = entry.vectorStatus === 'VECTORIZED' ? ' · Vector' : ''
        const detail = entry.type === 'FOLDER' ? 'Folder' : `${formatSize(entry.size)}${vectorLabel}`
        return (
          <div key={`${entry.type}-${entry.path}`} style={entryStyle}>
            {entry.type === 'FOLDER'
              ? <Folder size={19} color="var(--color-accent)" />
              : <File size={19} color="var(--color-text-tertiary)" />}
            <span style={{ flex: 1, minWidth: 0, textAlign: 'left', overflow: 'hidden', textOverflow: 'ellipsis' }}>{entry.name}</span>
            <span style={{ width: 90, textAlign: 'right', color: 'var(--color-text-tertiary)', fontSize: '0.75rem' }}>
              {detail}
            </span>
            {entry.type === 'FOLDER' && <Button variant="ghost" size="sm" onClick={() => onNavigate(entry.path)}>Open <ChevronRight size={13} /></Button>}
            <Button variant="ghost" size="sm" disabled={downloading} onClick={() => onDownload(entry.path, entry.type === 'FOLDER', entry.type === 'FOLDER' ? `${entry.name}.zip` : entry.name)} title={`Download ${entry.name}`}><Download size={14} /></Button>
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
  display: 'inline-flex', alignItems: 'center', gap: 5, border: 0, background: 'transparent',
  color: 'var(--color-text-secondary)', cursor: 'pointer', padding: 3, fontWeight: 500,
}

const entryStyle: React.CSSProperties = {
  width: '100%', display: 'flex', alignItems: 'center', gap: 10, padding: '0.8rem 1rem',
  border: 0, borderBottom: '1px solid var(--color-border)', background: 'transparent', color: 'var(--color-text)',
}

export function ItemsPage() {
  const qc = useQueryClient()
  const { user } = useAuthStore()
  const isAdmin = user?.role === 'ADMIN'
  const [showCreate, setShowCreate] = useState(false)
  const [editing, setEditing] = useState<string | null>(null)
  const [selected, setSelected] = useState<Item | null>(null)

  const { data, isLoading } = useQuery({ queryKey: ['items'], queryFn: () => itemsApi.list() })
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
    <div style={{ maxWidth: 800 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem', gap: 12 }}>
        <div>
          <h1 style={{ fontSize: '1.4rem', fontWeight: 700 }}>Storage</h1>
          <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.875rem' }}>
            Searchable storage roots backed by S3-compatible or Azure Blob storage
          </p>
          {!isAdmin && <p style={{ color: 'var(--color-text-tertiary)', fontSize: '0.8rem', marginTop: 6 }}>All authenticated users can explore storage. Only admins can manage roots.</p>}
        </div>
        {isAdmin && <Button onClick={() => setShowCreate(true)}><Plus size={15} /> New storage root</Button>}
      </div>

      {isAdmin && showCreate && <Card style={{ marginBottom: '1rem' }}><FolderForm onSubmit={create.mutate} onCancel={() => setShowCreate(false)} loading={create.isPending} /></Card>}
      {isLoading && <div style={{ display: 'flex', justifyContent: 'center', padding: '3rem' }}><Spinner /></div>}

      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
        {data?.content.map(folder => (
          <Card key={folder.id}>
            {isAdmin && editing === folder.id ? (
              <FolderForm initial={folder} loading={update.isPending} onSubmit={payload => update.mutate({ id: folder.id, ...payload })} onCancel={() => setEditing(null)} />
            ) : (
              <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12 }}>
                <div style={{ display: 'flex', gap: 12, flex: 1, minWidth: 0 }}>
                  <FolderOpen size={24} color="var(--color-accent)" />
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontWeight: 600, marginBottom: 4 }}>{folder.title}</div>
                    {folder.description && <p style={{ fontSize: '0.875rem', color: 'var(--color-text-secondary)', marginBottom: 8 }}>{folder.description}</p>}
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap', marginBottom: 8 }}>
                      <Badge color={folder.storageProvider === 'NONE' ? 'warning' : 'success'}>{folder.storageProvider}</Badge>
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, fontSize: '0.8rem', color: 'var(--color-text-secondary)' }}><Files size={13} /> {folder.documentCount} objects</span>
                    </div>
                    {folder.tags?.length ? <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>{folder.tags.map(tag => <Badge key={tag} color="accent">{tag}</Badge>)}</div> : null}
                    <div style={{ fontFamily: 'monospace', fontSize: '0.72rem', color: 'var(--color-text-tertiary)', marginTop: 8 }}>{folder.storagePrefix}</div>
                  </div>
                </div>
                <div style={{ display: 'flex', gap: 4, alignItems: 'flex-start', flexShrink: 0 }}>
                  <Button variant="secondary" size="sm" onClick={() => setSelected(folder)}>Open <ChevronRight size={13} /></Button>
                  {isAdmin && <Button variant="ghost" size="sm" onClick={() => setEditing(folder.id)}><Pencil size={13} /></Button>}
                  {isAdmin && <Button variant="ghost" size="sm" onClick={() => del.mutate(folder.id)}><Trash2 size={13} style={{ color: 'var(--color-danger)' }} /></Button>}
                </div>
              </div>
            )}
          </Card>
        ))}
        {data?.content.length === 0 && <div style={{ textAlign: 'center', padding: '3rem', color: 'var(--color-text-secondary)' }}>{isAdmin ? 'No storage roots yet. Create one above.' : 'No storage roots are available yet.'}</div>}
      </div>
    </div>
  )
}
