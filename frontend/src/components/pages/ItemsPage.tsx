import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { itemsApi, type Item } from '../../api/client'
import { Button, Card, Badge, Spinner } from '../ui'
import { Plus, Trash2, Pencil, X, Check } from 'lucide-react'

function ItemForm({ initial, onSubmit, onCancel, loading }: {
  initial?: Partial<Item>
  onSubmit: (data: { title: string; description?: string; tags?: string[] }) => void
  onCancel: () => void
  loading?: boolean
}) {
  const [title, setTitle] = useState(initial?.title ?? '')
  const [description, setDescription] = useState(initial?.description ?? '')
  const [tagsRaw, setTagsRaw] = useState(initial?.tags?.join(', ') ?? '')

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
      <input placeholder="Title *" value={title} onChange={e => setTitle(e.target.value)} />
      <textarea placeholder="Description" value={description} onChange={e => setDescription(e.target.value)} rows={3} />
      <input placeholder="Tags (comma separated)" value={tagsRaw} onChange={e => setTagsRaw(e.target.value)} />
      <div style={{ display: 'flex', gap: 8 }}>
        <Button loading={loading} onClick={() => onSubmit({ title, description, tags: tagsRaw ? tagsRaw.split(',').map(t => t.trim()) : [] })}>
          <Check size={14} /> Save
        </Button>
        <Button variant="ghost" onClick={onCancel}><X size={14} /> Cancel</Button>
      </div>
    </div>
  )
}

export function ItemsPage() {
  const qc = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [editing, setEditing] = useState<string | null>(null)

  const { data, isLoading } = useQuery({ queryKey: ['items'], queryFn: () => itemsApi.list() })

  const create = useMutation({
    mutationFn: itemsApi.create,
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['items'] }); setShowCreate(false) },
  })

  const update = useMutation({
    mutationFn: ({ id, ...data }: { id: string } & Parameters<typeof itemsApi.create>[0]) =>
      itemsApi.update(id, data),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['items'] }); setEditing(null) },
  })

  const del = useMutation({
    mutationFn: itemsApi.delete,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['items'] }),
  })

  return (
    <div style={{ maxWidth: 720 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
        <div>
          <h1 style={{ fontSize: '1.4rem', fontWeight: 700 }}>Items</h1>
          <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.875rem' }}>Stored in PostgreSQL · indexed in Qdrant</p>
        </div>
        <Button onClick={() => setShowCreate(true)}><Plus size={15} /> New item</Button>
      </div>

      {showCreate && (
        <Card style={{ marginBottom: '1rem' }}>
          <ItemForm onSubmit={create.mutate} onCancel={() => setShowCreate(false)} loading={create.isPending} />
        </Card>
      )}

      {isLoading && <div style={{ display: 'flex', justifyContent: 'center', padding: '3rem' }}><Spinner /></div>}

      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
        {data?.content.map(item => (
          <Card key={item.id}>
            {editing === item.id ? (
              <ItemForm
                initial={item}
                loading={update.isPending}
                onSubmit={d => update.mutate({ id: item.id, ...d })}
                onCancel={() => setEditing(null)}
              />
            ) : (
              <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12 }}>
                <div style={{ flex: 1 }}>
                  <div style={{ fontWeight: 600, marginBottom: 4 }}>{item.title}</div>
                  {item.description && (
                    <p style={{ fontSize: '0.875rem', color: 'var(--color-text-secondary)', marginBottom: 8 }}>
                      {item.description}
                    </p>
                  )}
                  {item.tags?.length ? (
                    <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
                      {item.tags.map(t => <Badge key={t} color="accent">{t}</Badge>)}
                    </div>
                  ) : null}
                  <div style={{ fontSize: '0.75rem', color: 'var(--color-text-tertiary)', marginTop: 8 }}>
                    {new Date(item.createdAt).toLocaleString()}
                  </div>
                </div>
                <div style={{ display: 'flex', gap: 4, flexShrink: 0 }}>
                  <Button variant="ghost" size="sm" onClick={() => setEditing(item.id)}><Pencil size={13} /></Button>
                  <Button variant="ghost" size="sm" onClick={() => del.mutate(item.id)}>
                    <Trash2 size={13} style={{ color: 'var(--color-danger)' }} />
                  </Button>
                </div>
              </div>
            )}
          </Card>
        ))}
        {data?.content.length === 0 && (
          <div style={{ textAlign: 'center', padding: '3rem', color: 'var(--color-text-secondary)' }}>
            No items yet. Create one above.
          </div>
        )}
      </div>
    </div>
  )
}
