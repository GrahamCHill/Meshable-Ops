import { pluginRole } from '../../../api/access'
import { useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuthStore } from '../../../hooks/useAuthStore'
import { Button } from '../../../components/ui'
import { boardsApi, blankBoard, type Board } from './api'
import { BoardEditor } from './BoardEditor'
import { useAvailablePlugins } from '../../../api/plugins'
import './kanban.css'

export default function KanbanPage() {
  const { '*': section = '' } = useParams()
  return section === 'guide' ? <div className="page-stack"><h1>Board guide</h1><p>Create a board, add lists for stages of work, then add cards. Move cards by dragging or choosing another list. Save changes before leaving.</p><p>Members see their own boards. Administrators can access all boards. Table permissions can make boards read only for members.</p><Link to="/plugins/kanban/boards">Open saved boards</Link></div> : <Boards />
}

function Boards() {
  const [params, setParams] = useSearchParams()
  const userId = useAuthStore(state => state.user?.id)
  const role = useAuthStore(state => pluginRole(state.user?.role ?? ''))
  const plugins = useAvailablePlugins()
  const canWrite = !!role && !!plugins.data?.find(item => item.manifest.id === 'kanban')?.tableAccess.plugin_kanban_boards?.writeRoles.includes(role)
  const client = useQueryClient()
  const selected = params.get('board') ?? ''
  const setSelected = (id: string) => setParams(id ? {board:id} : {})
  const [dirty, setDirty] = useState(false)
  const [fresh, setFresh] = useState(blankBoard)
  const [generation, setGeneration] = useState(0)
  const list = useQuery({ queryKey: ['plugin-kanban-boards', userId, role], queryFn: boardsApi.list })
  const board = useQuery({ queryKey: ['plugin-kanban-board', userId, role, selected], queryFn: () => boardsApi.get(selected), enabled: !!selected && selected !== 'new', refetchOnWindowFocus: false })
  const saved = (value: Board) => {
    client.setQueryData(['plugin-kanban-board', userId, role, value.id], value)
    void client.invalidateQueries({ queryKey: ['plugin-kanban-boards', userId, role] })
    setSelected(value.id); setDirty(false)
  }
  return <div className="page-stack">
    <div className="page-heading"><div><h1>Boards</h1><p>Organize work with lists and cards.</p></div><Button disabled={dirty || !canWrite} onClick={() => { setFresh(blankBoard()); setSelected('new'); setGeneration(value => value + 1) }}>New board</Button></div>
    {!canWrite && <p className="field-help">Your role has read-only access to these boards.</p>}
    <div className="inline-actions"><label className="form-field">Saved board<select value={selected} disabled={dirty} onChange={event => setSelected(event.target.value)}><option value="">Select a board</option>{selected === 'new' && <option value="new">Unsaved board</option>}{list.data?.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>{dirty && <Button variant="ghost" onClick={() => { setDirty(false); setGeneration(value => value + 1); if (selected === 'new') setSelected(''); else void board.refetch() }}>Discard changes</Button>}</div>
    {list.isLoading && <p role="status">Loading boards…</p>}
    {list.isError && <p role="alert">Unable to load boards. <button onClick={() => void list.refetch()}>Retry</button></p>}
    {!selected && list.data?.length === 0 && <p>Create your first board to get started.</p>}
    {selected && selected !== 'new' && board.isLoading && <p role="status">Loading board…</p>}
    {selected && selected !== 'new' && board.isError && <p role="alert">This board is unavailable. <button onClick={() => void board.refetch()}>Retry</button></p>}
    <fieldset disabled={!canWrite} className="page-stack">{selected === 'new' ? <BoardEditor key={`new-${generation}`} initial={fresh} onSaved={saved} onDirty={setDirty} readOnly={!canWrite} /> : !board.isError && board.data && selected === board.data.id && <BoardEditor key={`${board.data.id}-${board.data.revision}-${generation}`} id={board.data.id} initial={board.data} onSaved={saved} onDirty={setDirty} readOnly={!canWrite} />}</fieldset>
  </div>
}
