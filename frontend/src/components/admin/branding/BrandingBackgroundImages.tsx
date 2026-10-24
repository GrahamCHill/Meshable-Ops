import { useEffect, useRef, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { brandingApi, loginImageUrl, type LoginImage } from '../../../api/branding'
import { getErrorMessage } from '../adminUtils'
import { Button } from '../../ui'

function BackgroundImage({ slot, image }: { slot: number; image?: LoginImage }) {
  const client = useQueryClient()
  const input = useRef<HTMLInputElement>(null)
  const [file, setFile] = useState<File>()
  const [preview, setPreview] = useState<string>()
  const [error, setError] = useState('')
  useEffect(() => {
    if (!file) { setPreview(undefined); return }
    const url = URL.createObjectURL(file); setPreview(url)
    return () => URL.revokeObjectURL(url)
  }, [file])
  const save = useMutation({ mutationFn: (remove: boolean) => remove ? brandingApi.removeBackground(slot) : brandingApi.uploadBackground(slot, file!), onSuccess: data => {
    client.setQueryData(['branding'], data); setFile(undefined); if (input.current) input.current.value = ''
  } })
  return <section className="page-stack">
    <h3>{image ? `Image ${slot + 1}` : 'Add a sign-in image'}</h3>
    {(preview || image) && <img src={preview ?? loginImageUrl(image!)} alt={`Sign-in background ${slot + 1}`} />}
    <label className="form-field">{image ? `Replace image ${slot + 1}` : `Choose image ${slot + 1}`}<input ref={input} type="file" accept="image/png,image/jpeg,image/webp" disabled={save.isPending} onChange={event => {
      const selected = event.target.files?.[0]; setError(''); save.reset(); setFile(undefined)
      if (selected && (!['image/png', 'image/jpeg', 'image/webp'].includes(selected.type) || selected.size > 5 * 1024 * 1024)) { setError('Choose a PNG, JPEG, or WebP up to 5 MiB.'); event.target.value = ''; return }
      setFile(selected)
    }} /></label>
    <div className="inline-actions"><Button size="sm" disabled={!file || save.isPending} onClick={() => save.mutate(false)}>{image ? 'Replace image' : 'Upload image'}</Button>{image && <Button size="sm" variant="ghost" disabled={save.isPending} onClick={() => save.mutate(true)}>Remove image {slot + 1}</Button>}</div>
    {(error || save.isError) && <p role="alert" className="feedback error">{error || getErrorMessage(save.error)}</p>}
    {save.isSuccess && <p role="status">Image updated.</p>}
  </section>
}

export function BrandingBackgroundImages({ images }: { images: LoginImage[] }) {
  const next = [0, 1, 2, 3, 4].find(slot => !images.some(image => image.slot === slot))
  return <section className="page-stack">
    <h2>Sign-in images</h2>
    <p className="field-help">Recommended: 1920 × 1080 px. PNG, JPEG, or WebP up to 5 MiB and 4096 × 4096 pixels. Up to five images, shown in numbered order. Images are cropped to fill the screen; keep important content near the centre. Uploads and removals take effect immediately.</p>
    <div className="branding-backgrounds">{images.map(image => <BackgroundImage key={image.slot} slot={image.slot} image={image} />)}{next !== undefined && <BackgroundImage key={`new-${next}`} slot={next} />}</div>
  </section>
}
