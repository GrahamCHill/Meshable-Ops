import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { itemsApi } from '../../api/items'
import { type AccessRoot, type StorageRule } from '../../api/access'
import { Button } from '../ui'

export function AccessStoragePicker({ roots, onSelect }: { roots: AccessRoot[]; onSelect: (rule: StorageRule) => void }) {
  const [root, setRoot] = useState('')
  const [path, setPath] = useState('')
  const contents = useQuery({ queryKey: ['access-storage-picker', root, path], queryFn: () => itemsApi.contents(root, path), enabled: !!root })
  return <div className="page-stack access-picker">
    <label>Storage root<select value={root} onChange={e => { setRoot(e.target.value); setPath('') }}><option value="">Choose a storage root</option>{roots.filter(r => r.accessible).map(r => <option key={r.id} value={r.id}>{r.connection ? `${r.connection} / ` : ''}{r.title}</option>)}</select></label>
    {root && <>
      <div className="inline-actions"><span className="access-path">/{path}</span>{path && <Button variant="ghost" size="sm" onClick={() => setPath(path.slice(0, path.lastIndexOf('/', path.length - 2) + 1))}>Up one folder</Button>}<Button variant="secondary" size="sm" onClick={() => onSelect({folderId: root, path, type: 'FOLDER'})}>Select this directory</Button></div>
      {contents.isLoading && <p role="status">Loading files…</p>}
      {contents.isError && <p role="alert">Unable to browse this storage root. Check its connection.</p>}
      <div className="access-file-list">{contents.data?.entries.map(entry => <div className="access-file-row" key={entry.path}>
        {entry.type === 'FOLDER' ? <button type="button" className="utility-button" onClick={() => setPath(entry.path)}>{entry.name}/</button> : <span>{entry.name}</span>}
        <Button variant="ghost" size="sm" onClick={() => onSelect({folderId: root, path: entry.path, type: entry.type === 'FOLDER' ? 'FOLDER' : 'FILE'})}>Select {entry.type === 'FOLDER' ? 'directory' : 'file'}</Button>
      </div>)}</div>
      {!contents.isLoading && !contents.isError && !contents.data?.entries.length && <p className="field-help">This directory is empty.</p>}
    </>}
  </div>
}

export function AccessRuleList({ rules, roots, remove }: { rules: StorageRule[]; roots: AccessRoot[]; remove: (index: number) => void }) {
  return <ul className="access-rules">{rules.map((rule, index) => <li key={`${rule.folderId}-${rule.type}-${rule.path}`}><span>{roots.find(root => root.id === rule.folderId)?.title ?? 'Removed storage root'} / {rule.path || '(entire root)'} <small>{rule.type === 'FILE' ? 'File' : 'Directory and descendants'}</small></span><Button variant="ghost" size="sm" onClick={() => remove(index)}>Remove</Button></li>)}</ul>
}
