import { useState } from 'react'
import type { Item } from '../../api/items'
import { Button } from '../ui'

export function FolderForm({
  initial,
  onSubmit,
  onCancel,
  loading,
}: Readonly<{
  initial?: Partial<Item>
  onSubmit: (data: { title: string; description?: string; tags?: string[] }) => void
  onCancel: () => void
  loading?: boolean
}>) {
  const [title, setTitle] = useState(initial?.title ?? '')
  const [description, setDescription] = useState(initial?.description ?? '')
  const [tagsRaw, setTagsRaw] = useState(initial?.tags?.join(', ') ?? '')

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
      <input placeholder="Storage root name *" value={title} onChange={e => setTitle(e.target.value)} />
      <textarea placeholder="Description" value={description} onChange={e => setDescription(e.target.value)} rows={3} />
      <input placeholder="Tags (comma separated)" value={tagsRaw} onChange={e => setTagsRaw(e.target.value)} />
      <div style={{ display: 'flex', gap: 8 }}>
        <Button
          loading={loading}
          onClick={() => onSubmit({
            title,
            description,
            tags: tagsRaw ? tagsRaw.split(',').map(tag => tag.trim()) : [],
          })}
        >
          Save
        </Button>
        <Button variant="ghost" onClick={onCancel}> Cancel</Button>
      </div>
    </div>
  )
}
