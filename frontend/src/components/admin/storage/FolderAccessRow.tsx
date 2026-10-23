import { useEffect, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { storageConnectionsApi, type SharedFolder } from '../../../api/storageConnections'
import { Button } from '../../ui'
import { getErrorMessage } from '../adminUtils'
import { useRefreshStorage } from './useRefreshStorage'

export function FolderAccessRow({ folder, removable = false }: { folder: SharedFolder; removable?: boolean }) {
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
