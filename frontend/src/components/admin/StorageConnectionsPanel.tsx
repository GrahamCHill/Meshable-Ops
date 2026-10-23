import { useEffect, useId, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { storageConnectionsApi, type SharedFolder, type StorageConnection, type StorageConnectionInput } from '../../api/storageConnections'
import { Badge, Button, Card, Spinner } from '../ui'
import { getErrorMessage } from './adminUtils'

function useRefreshStorage() {
  const client = useQueryClient()
  return () => {
    for (const key of ['storage-connections', 'admin-storage-connections', 'admin-storage-folders', 'items', 'storage-contents']) client.invalidateQueries({ queryKey: [key] })
  }
}

function ConnectionForm({ initial, onClose }: { initial?: StorageConnection; onClose: () => void }) {
  const id = useId()
  const refresh = useRefreshStorage()
  const [form, setForm] = useState<StorageConnectionInput>({
    nickname: initial?.nickname ?? '', provider: initial?.provider ?? 'S3', bucket: initial?.bucket ?? '',
    region: initial?.region ?? 'us-east-1', endpoint: initial?.endpoint ?? '', accessKey: initial?.accessKey ?? '',
    secretKey: '', useServerIdentity: initial ? !initial.secretConfigured && !initial.accessKey : true,
    basePath: initial?.basePath ?? '', enabled: initial?.enabled ?? true,
  })
  const save = useMutation({ mutationFn: storageConnectionsApi.save, onSuccess: () => { refresh(); onClose() } })
  const azure = form.provider === 'AZURE_BLOB'
  const locationLocked = !!initial?.folders.length
  const field = (key: keyof StorageConnectionInput, value: string | boolean) => setForm(previous => ({ ...previous, [key]: value }))
  return <Card>
    <form className="page-stack" onSubmit={event => { event.preventDefault(); save.mutate({ id: initial?.id, data: form }) }}>
      <div className="page-heading"><h2 className="card-title">{initial ? 'Edit connection' : 'Add a bucket or container'}</h2><Button type="button" variant="ghost" size="sm" onClick={onClose}> Close</Button></div>
      <fieldset className="settings-fieldset form-grid" disabled={save.isPending}>
        <div className="form-field"><label htmlFor={`${id}-nickname`}>In-app nickname</label><input id={`${id}-nickname`} required maxLength={100} placeholder="Legal archive" value={form.nickname} onChange={event => field('nickname', event.target.value)} /><small>This appears as a folder in Storage.</small></div>
        <div className="form-field"><label htmlFor={`${id}-provider`}>Provider</label><select id={`${id}-provider`} disabled={locationLocked} value={form.provider} onChange={event => setForm(previous => ({ ...previous, provider: event.target.value as StorageConnectionInput['provider'], bucket: '', endpoint: '', accessKey: '', secretKey: '', useServerIdentity: true }))}><option value="S3">Amazon S3 / compatible</option><option value="AZURE_BLOB">Azure Blob</option></select></div>
        <div className="form-field"><label htmlFor={`${id}-bucket`}>{azure ? 'Container name' : 'Bucket name'}</label><input id={`${id}-bucket`} disabled={locationLocked} required maxLength={255} value={form.bucket} onChange={event => field('bucket', event.target.value)} /></div>
        {!azure && <div className="form-field"><label htmlFor={`${id}-region`}>Region</label><input id={`${id}-region`} maxLength={255} placeholder="us-east-1" value={form.region} onChange={event => field('region', event.target.value)} /></div>}
        <div className="form-field full-width"><label htmlFor={`${id}-endpoint`}>{azure ? 'Blob service endpoint' : 'Custom endpoint (optional)'}</label><input id={`${id}-endpoint`} disabled={locationLocked} type="url" required={azure} maxLength={512} placeholder={azure ? 'https://account.blob.core.windows.net' : 'http://minio:9000'} value={form.endpoint} onChange={event => field('endpoint', event.target.value)} /></div>
        <div className="form-field full-width"><label htmlFor={`${id}-base`}>Start folder (optional)</label><input id={`${id}-base`} disabled={locationLocked} maxLength={512} placeholder="Leave blank to choose folders from the bucket/container root" value={form.basePath} onChange={event => field('basePath', event.target.value)} /><small>Limits the admin folder picker to this prefix. Saving a connection shares no folders automatically.</small></div>
        {locationLocked && <p className="field-help full-width">The storage location is fixed while folders are selected. Remove those selections first to repoint this connection.</p>}
        <div className="form-field full-width"><label htmlFor={`${id}-auth`}>Authentication</label><select id={`${id}-auth`} value={form.useServerIdentity ? 'identity' : 'keys'} onChange={event => field('useServerIdentity', event.target.value === 'identity')}><option value="identity">Use server identity</option><option value="keys">{azure ? 'Storage account key' : 'Access keys'}</option></select></div>
        {!form.useServerIdentity && <><div className="form-field"><label htmlFor={`${id}-access`}>{azure ? 'Storage account name' : 'Access key ID'}</label><input id={`${id}-access`} required autoComplete="off" maxLength={255} value={form.accessKey} onChange={event => field('accessKey', event.target.value)} /></div><div className="form-field"><label htmlFor={`${id}-secret`}>{azure ? 'Storage account key' : 'Secret access key'}</label><input id={`${id}-secret`} type="password" required={!initial?.secretConfigured} autoComplete="new-password" maxLength={512} placeholder={initial?.secretConfigured ? 'Leave blank to keep saved secret' : ''} value={form.secretKey} onChange={event => field('secretKey', event.target.value)} /></div></>}
        <label className="checkbox-label full-width"><input type="checkbox" checked={form.enabled} onChange={event => field('enabled', event.target.checked)} /> Enable this connection in Storage</label>
      </fieldset>
      {save.isError && <p role="alert" className="feedback error">{getErrorMessage(save.error)}</p>}
      <div className="inline-actions"><Button type="submit" loading={save.isPending}> Save connection</Button><Button type="button" variant="ghost" onClick={onClose}>Cancel</Button></div>
    </form>
  </Card>
}

function FolderAccessRow({ folder, removable = false }: { folder: SharedFolder; removable?: boolean }) {
  const refresh = useRefreshStorage()
  const [nickname, setNickname] = useState(folder.nickname)
  const [accessible, setAccessible] = useState(folder.accessible)
  useEffect(() => { setNickname(folder.nickname); setAccessible(folder.accessible) }, [folder.nickname, folder.accessible])
  const update = useMutation({ mutationFn: storageConnectionsApi.updateFolder, onSuccess: refresh })
  const remove = useMutation({ mutationFn: storageConnectionsApi.removeFolder, onSuccess: refresh })
  return <div className="shared-folder-row">

    <div className="shared-folder-name"><input aria-label={`Nickname for ${folder.nickname}`} maxLength={255} value={nickname} onChange={event => setNickname(event.target.value)} /><small>{folder.path || '(entire connection)'}</small></div>
    <label className="checkbox-label"><input type="checkbox" checked={accessible} onChange={event => setAccessible(event.target.checked)} /> Accessible</label>
    <Button variant="secondary" size="sm" loading={update.isPending} disabled={!nickname.trim() || (nickname === folder.nickname && accessible === folder.accessible)} onClick={() => update.mutate({ id: folder.id, nickname, accessible })}>Save</Button>
    {removable && <Button variant="ghost" size="sm" aria-label={`Remove selection ${folder.nickname}`} title="Remove the selection; cloud files remain" loading={remove.isPending} onClick={() => remove.mutate(folder.id)}>Remove</Button>}
    {(update.isError || remove.isError) && <p role="alert" className="feedback error full-width">{getErrorMessage(update.error ?? remove.error)}</p>}
  </div>
}

function FolderPicker({ connection }: { connection: StorageConnection }) {
  const refresh = useRefreshStorage()
  const [path, setPath] = useState('')
  const [jump, setJump] = useState('')
  const [selected, setSelected] = useState<string | null>(null)
  const [nickname, setNickname] = useState('')
  const browse = useQuery({ queryKey: ['admin-storage-browse', connection.id, path], queryFn: () => storageConnectionsApi.browse(connection.id, path), retry: false })
  const share = useMutation({ mutationFn: storageConnectionsApi.share, onSuccess: () => { refresh(); setSelected(null); setNickname('') } })
  const parts = path.split('/').filter(Boolean)
  const navigate = (next: string) => { setPath(next); setJump(next); setSelected(null); share.reset() }
  const choose = (folder: string, label: string) => { setSelected(folder); setNickname(label); share.reset() }
  return <div className="folder-picker page-stack">
    <div><h3 className="card-title">Choose accessible folders</h3><p className="field-help">Only selected folders appear in Storage. A selected folder includes all of its subfolders and files.</p></div>
    <form className="inline-actions" onSubmit={event => { event.preventDefault(); navigate(jump) }}><input aria-label="Folder path to browse" placeholder="Folder path relative to the start folder" value={jump} onChange={event => setJump(event.target.value)} style={{ flex: 1, minWidth: 180 }} /><Button type="submit" variant="secondary" size="sm">Go to folder</Button></form>
    <nav className="folder-breadcrumbs" aria-label="Folder picker path"><button onClick={() => navigate('')}>{connection.nickname}</button>{parts.map((part, index) => <span key={index}><span aria-hidden="true">/</span><button onClick={() => navigate(`${parts.slice(0, index + 1).join('/')}/`)}>{part}</button></span>)}</nav>
    <div className="inline-actions"><Button variant="secondary" size="sm" disabled={browse.isLoading || browse.isError} onClick={() => choose(path, parts[parts.length - 1] ?? connection.nickname)}> Share this folder</Button><span className="field-help" style={{ margin: 0 }}>{path || connection.basePath || 'Bucket / container root'}</span></div>
    {browse.isLoading && <div role="status"><Spinner /> Loading folders…</div>}
    {browse.isError && <div role="alert" className="feedback error">{getErrorMessage(browse.error, 'Unable to browse this path.')}<Button variant="ghost" size="sm" onClick={() => browse.refetch()}>Retry</Button></div>}
    {browse.data && <div className="folder-picker-list">{browse.data.entries.map(entry => <div className="folder-picker-entry" key={entry.path}><button className="folder-open" onClick={() => navigate(entry.path)}><span>{entry.name}</span></button>{connection.folders.some(folder => folder.path === entry.path) ? <Badge color="accent">Selected</Badge> : <Button variant="secondary" size="sm" onClick={() => choose(entry.path, entry.name)}>Select folder</Button>}</div>)}{!browse.data.entries.length && <p className="field-help">No child folders at this path. You can share this folder to include its files.</p>}</div>}
    {selected !== null && <form className="page-stack share-folder-form" onSubmit={event => { event.preventDefault(); share.mutate({ id: connection.id, path: selected, nickname }) }}><div className="form-field"><label htmlFor={`share-${connection.id}`}>Folder nickname in the app</label><input id={`share-${connection.id}`} autoFocus required maxLength={255} value={nickname} onChange={event => setNickname(event.target.value)} /><small>Share: {connection.basePath + selected || 'Entire bucket / container'}</small></div>{selected === '' && <p className="feedback warning">This selection exposes everything beneath the connection's start folder to signed-in users.</p>}<div className="inline-actions"><Button type="submit" loading={share.isPending}> Add accessible folder</Button><Button type="button" variant="ghost" onClick={() => setSelected(null)}>Cancel</Button></div>{share.isError && <p role="alert" className="feedback error">{getErrorMessage(share.error)}</p>}</form>}
  </div>
}

function ConnectionCard({ connection, onEdit }: { connection: StorageConnection; onEdit: () => void }) {
  const refresh = useRefreshStorage()
  const [picker, setPicker] = useState(false)
  const test = useMutation({ mutationFn: storageConnectionsApi.test })
  const remove = useMutation({ mutationFn: storageConnectionsApi.remove, onSuccess: refresh })
  return <Card>
    <div className="page-heading"><div className="connection-heading"><div><h2 className="card-title" style={{ marginBottom: 4 }}>{connection.nickname}</h2><p className="field-help" style={{ margin: 0 }}>{connection.provider === 'S3' ? 'S3' : 'Azure Blob'} · {connection.bucket}{connection.basePath ? ` / ${connection.basePath}` : ''}</p></div><Badge color={connection.enabled ? 'success' : 'default'}>{connection.enabled ? 'Enabled' : 'Hidden'}</Badge></div><div className="inline-actions"><Button variant="ghost" size="sm" onClick={onEdit}> Edit</Button><Button variant="ghost" size="sm" loading={test.isPending} onClick={() => test.mutate(connection.id)}> Test connection</Button><Button variant="ghost" size="sm" aria-label={`Remove connection ${connection.nickname}`} disabled={!!connection.folders.length} title={connection.folders.length ? 'Remove selected folders first' : 'Remove connection'} loading={remove.isPending} onClick={() => remove.mutate(connection.id)}>Remove connection</Button></div></div>
    <p className="field-help">Visible to all signed-in users when enabled. Connection and folder nicknames change only their names inside the app.</p>
    {connection.folders.map(folder => <FolderAccessRow key={folder.id} folder={folder} removable />)}
    {!connection.folders.length && <p className="field-help">No folders selected. This connection is not yet visible in Storage.</p>}
    <div className="inline-actions" style={{ marginTop: 16 }}><Button variant="secondary" size="sm" aria-expanded={picker} onClick={() => setPicker(!picker)}>{picker ? 'Close folder picker' : 'Choose folders'}</Button></div>
    {test.isSuccess && <p role="status" className="feedback success">{test.data.message}</p>}
    {(test.isError || remove.isError) && <p role="alert" className="feedback error">{getErrorMessage(test.error ?? remove.error)}</p>}
    {picker && <FolderPicker connection={connection} />}
  </Card>
}

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
