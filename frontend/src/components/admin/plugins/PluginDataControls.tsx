import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { pluginsApi, type PluginView } from '../../../api/plugins'
import { getErrorMessage } from '../adminUtils'
import { Button } from '../../ui'

export function PluginDataControls({plugin}: {plugin: PluginView}) {
  const [open, setOpen] = useState(false)
  const [confirmation, setConfirmation] = useState('')
  const client = useQueryClient()
  const clear = useMutation({mutationFn: () => pluginsApi.clear(plugin.manifest.id, confirmation), onSuccess: async () => {
    setConfirmation(''); setOpen(false)
    await client.invalidateQueries({predicate: query => String(query.queryKey[0]).startsWith(`plugin-${plugin.manifest.id}`)})
  }})
  return <div className="page-stack">
    <div><Button variant="secondary" onClick={() => {setOpen(!open); clear.reset()}}>Clear plugin data</Button></div>
    {open && <form className="page-stack" onSubmit={event => {event.preventDefault(); clear.mutate()}}>
      <p role="alert">This permanently deletes all records from {plugin.manifest.tables.map(table => table.name).join(', ')}. Table definitions and plugin settings stay in place. Take a database backup first.</p>
      <label className="form-field">Type {plugin.manifest.id} to confirm<input value={confirmation} autoComplete="off" onChange={event => setConfirmation(event.target.value)} /></label>
      <div className="inline-actions"><Button type="submit" disabled={clear.isPending || confirmation !== plugin.manifest.id}>Permanently clear data</Button><Button variant="ghost" type="button" disabled={clear.isPending} onClick={() => {setOpen(false); setConfirmation('')}}>Cancel</Button></div>
    </form>}
    {clear.isError && <p className="feedback error" role="alert">{getErrorMessage(clear.error)}</p>}
    {clear.isSuccess && <p role="status">Plugin data cleared. {Object.values(clear.data.deletedRows).reduce((sum, value) => sum + value, 0)} rows deleted.</p>}
  </div>
}
