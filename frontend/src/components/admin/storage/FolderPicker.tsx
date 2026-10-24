import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { storageConnectionsApi, type StorageConnection } from '../../../api/storageConnections'
import { Badge, Button, Spinner } from '../../ui'
import { getErrorMessage } from '../adminUtils'
import { useRefreshStorage } from './useRefreshStorage'

export function FolderPicker({ connection }: { connection: StorageConnection }) {
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
    {selected !== null && <form className="page-stack share-folder-form" onSubmit={event => { event.preventDefault(); share.mutate({ id: connection.id, path: selected, nickname }) }}><div className="form-field"><label htmlFor={`share-${connection.id}`}>Folder nickname in the app</label><input id={`share-${connection.id}`} required maxLength={255} value={nickname} onChange={event => setNickname(event.target.value)} /><small>Share: {connection.basePath + selected || 'Entire bucket / container'}</small></div>{selected === '' && <p className="feedback warning">This selection exposes everything beneath the connection's start folder to signed-in users.</p>}<div className="inline-actions"><Button type="submit" loading={share.isPending}> Add accessible folder</Button><Button type="button" variant="ghost" onClick={() => setSelected(null)}>Cancel</Button></div>{share.isError && <p role="alert" className="feedback error">{getErrorMessage(share.error)}</p>}</form>}
  </div>
}
