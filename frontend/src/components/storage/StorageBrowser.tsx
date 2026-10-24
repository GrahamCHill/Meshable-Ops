import { useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { itemsApi, type Item } from '../../api/items'
import { useAuthStore } from '../../hooks/useAuthStore'
import { Badge, Button, Card, Spinner } from '../ui'
import { StorageEntries } from './StorageEntries'
import { PdfViewer } from './PdfViewer'
import { StorageSearch } from '../search/StorageSearch'
import { useDocumentAccess } from '../../api/documentAccess'
import { sharesApi } from '../../api/shares'
import { SharePdfDialog } from './SharePdfDialog'

export function StorageBrowser({ folder, onClose, initialPath = '' }: Readonly<{ folder: Item; onClose: () => void; initialPath?: string }>) {
  const qc = useQueryClient()
  const { user } = useAuthStore()
  const [path, setPath] = useState(initialPath)
  const [transferError, setTransferError] = useState('')
  const [pdfPath, setPdfPath] = useState<string>()
  const [sharePath, setSharePath] = useState<string>()
  const access = useDocumentAccess()
  const sharing = useQuery({ queryKey: ['share-settings'], queryFn: sharesApi.settings, enabled: user?.role === 'ADMIN' })
  const canShare = user?.role === 'ADMIN' && sharing.data?.enabled && sharing.data.emailAvailable && access.data && access.data.downloadMode !== 'DISABLED'
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
          {access.data && access.data.downloadMode !== 'DISABLED' && <Button variant="secondary" size="sm" loading={download.isPending} onClick={() => download.mutate({ objectPath: path, archive: true, filename: `${segments[segments.length - 1] ?? folder.title}.zip` })}> Download folder</Button>}
          {user?.canUploadDocuments && <>
            <Button variant="secondary" size="sm" loading={upload.isPending} onClick={() => fileInput.current?.click()}> Upload files</Button>
            <Button size="sm" loading={upload.isPending} onClick={() => folderInput.current?.click()}> Upload folder</Button>
            <input ref={fileInput} type="file" multiple hidden onChange={event => { selectFiles(event.target.files); event.target.value = '' }} />
            <input ref={element => { folderInput.current = element; element?.setAttribute('webkitdirectory', '') }} type="file" multiple hidden onChange={event => { selectFiles(event.target.files); event.target.value = '' }} />
          </>}
        </div>
      </div>

      {transferError && <div style={{ color: 'var(--color-danger)', fontSize: '0.82rem', marginBottom: '0.75rem' }}>{transferError}</div>}
      {access.isError && <p role="alert">Document controls are unavailable. Try refreshing the page.</p>}
      <StorageSearch key={`${folder.id}-${path}`} folderId={folder.id} path={path} onOpenPdf={access.data?.pdfPreviewEnabled ? setPdfPath : undefined} />

      <Card style={{ padding: 0, overflow: 'hidden' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '0.85rem 1rem', flexWrap: 'wrap' }}>
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
        {current && <StorageEntries contents={current} parentPath={parentPath} onNavigate={setPath} onDownload={(objectPath, archive, filename) => download.mutate({ objectPath, archive, filename })} downloading={download.isPending} policy={access.data} onOpenPdf={setPdfPath} onSharePdf={canShare ? setSharePath : undefined} />}
      </Card>

      <Card style={{ marginTop: '1rem', background: 'var(--color-surface-2)' }}>
        <div style={{ fontSize: '0.8rem', color: 'var(--color-text-secondary)' }}>
          Browse files within this shared folder.
        </div>
      </Card>
      {pdfPath && access.data?.pdfPreviewEnabled && <PdfViewer key={pdfPath} folderId={folder.id} path={pdfPath} policy={access.data} onClose={() => setPdfPath(undefined)} onDownload={(objectPath, archive, filename) => download.mutate({ objectPath, archive, filename })} downloading={download.isPending} />}
      {sharePath && canShare && <SharePdfDialog folderId={folder.id} path={sharePath} maxLifetimeHours={sharing.data!.maxLifetimeHours} onClose={() => setSharePath(undefined)} />}
    </div>
  )
}

const breadcrumbStyle: React.CSSProperties = {
  display: 'inline-flex', alignItems: 'center', gap: 5, background: 'transparent',
  color: 'var(--color-text-secondary)', cursor: 'pointer', padding: 3, fontWeight: 500,
}
