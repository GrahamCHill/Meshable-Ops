import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { storageConnectionsApi, type StorageConnection } from '../../../api/storageConnections'
import { Badge, Button, Card } from '../../ui'
import { getErrorMessage } from '../adminUtils'
import { useRefreshStorage } from './useRefreshStorage'
import { FolderAccessRow } from './FolderAccessRow'
import { FolderPicker } from './FolderPicker'

export function ConnectionCard({ connection, onEdit }: { connection: StorageConnection; onEdit: () => void }) {
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
