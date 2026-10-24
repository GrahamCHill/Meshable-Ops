import {useState} from 'react'
import {Button} from '../../../components/ui'
import {usePageDraft} from '../../../hooks/usePageDraft'
import type {BoardCard} from './api'

export function QuickCreate({draftKey, busy, onCreate, onCancel}: Readonly<{
  draftKey: string; busy: boolean; onCreate: (title: string, type: BoardCard['type']) => Promise<void>; onCancel: () => void
}>) {
  const [title, setTitle, clear] = usePageDraft(draftKey, '')
  const [type, setType] = useState<BoardCard['type']>('TASK')
  const submit = async () => {await onCreate(title.trim(), type); clear()}
  return <form className="kanban-quick-create" onSubmit={event => {event.preventDefault(); if (title.trim() && !busy) void submit().catch(() => undefined)}}>
    <textarea autoFocus aria-label="New ticket title" placeholder="What needs to be done?" maxLength={160} rows={3} value={title} disabled={busy} onChange={event => setTitle(event.target.value)} onKeyDown={event => {
      if (event.key === 'Escape') {event.stopPropagation(); if (!busy) onCancel()}
      if (event.key === 'Enter' && !event.shiftKey) {event.preventDefault(); event.currentTarget.form?.requestSubmit()}
    }}/>
    <div className="inline-actions"><select aria-label="New ticket type" disabled={busy} value={type} onChange={event => setType(event.target.value as BoardCard['type'])}><option value="TASK">Task</option><option value="STORY">Story</option><option value="BUG">Bug</option></select><Button type="submit" size="sm" loading={busy} disabled={!title.trim()}>Create</Button><Button type="button" size="sm" variant="ghost" disabled={busy} onClick={onCancel}>Cancel</Button></div>
    <small>Enter to create · Shift+Enter for a new line</small>
  </form>
}
