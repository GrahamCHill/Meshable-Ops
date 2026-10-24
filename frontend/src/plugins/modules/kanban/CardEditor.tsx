import { useState } from 'react'
import { Button } from '../../../components/ui'
import type { BoardCard } from './api'

export function CardEditor({ card, onSave, onCancel }: { card: BoardCard; onSave: (card: BoardCard) => void; onCancel: () => void }) {
  const [title, setTitle] = useState(card.title)
  const [description, setDescription] = useState(card.description)
  return <form className="page-stack" onSubmit={event => { event.preventDefault(); if (title.trim()) onSave({ ...card, title: title.trim(), description }) }}>
    <label className="form-field">Card title<input autoFocus required maxLength={160} value={title} onChange={event => setTitle(event.target.value)} /></label>
    <label className="form-field">Description<textarea rows={3} maxLength={2000} value={description} onChange={event => setDescription(event.target.value)} /></label>
    <div className="inline-actions"><Button type="submit" disabled={!title.trim()}>Apply card</Button><Button type="button" variant="ghost" onClick={onCancel}>Cancel</Button></div>
  </form>
}
