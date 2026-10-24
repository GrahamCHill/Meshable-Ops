import { useEffect, useRef, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { brandImage, brandingApi, type Branding, type BrandingKind } from '../../../api/branding'
import { getErrorMessage } from '../adminUtils'
import { Button } from '../../ui'

export function BrandImageSetting({ kind, branding }: { kind: BrandingKind; branding: Branding }) {
  const client = useQueryClient()
  const input = useRef<HTMLInputElement>(null)
  const [file, setFile] = useState<File>()
  const [preview, setPreview] = useState<string>()
  const [error, setError] = useState('')
  const title = kind === 'LOGO' ? 'Logo' : 'Favicon'
  useEffect(() => {
    if (!file) { setPreview(undefined); return }
    const url = URL.createObjectURL(file); setPreview(url)
    return () => URL.revokeObjectURL(url)
  }, [file])
  const save = useMutation({ mutationFn: (reset: boolean) => reset ? brandingApi.reset(kind) : brandingApi.upload(kind, file!), onSuccess: data => {
    client.setQueryData(['branding'], data); setFile(undefined); if (input.current) input.current.value = ''
  } })
  const current = kind === 'FAVICON' && !branding.faviconVersion ? brandImage(branding, 'LOGO') : brandImage(branding, kind)
  const customized = kind === 'LOGO' ? branding.logoVersion : branding.faviconVersion
  return <section className="branding-image-setting">
    <img className="branding-preview" src={preview ?? current} alt={`${title} ${file ? 'preview' : 'current image'}`} />
    <div className="page-stack"><h3>{title}</h3>
      <p className="field-help">{kind === 'LOGO' ? 'Recommended: 256 × 256 px with transparency. Appears in navigation and above the sign-in form.' : 'Recommended: 64 × 64 px. Use a simple mark that is clear at 16 × 16 px. Falls back to the logo.'}</p>
      <label className="form-field" htmlFor={`branding-${kind}`}>Choose {title.toLowerCase()}<input ref={input} id={`branding-${kind}`} type="file" accept="image/png,image/jpeg,image/webp" disabled={save.isPending} onChange={event => {
        const selected = event.target.files?.[0]; setError(''); save.reset(); setFile(undefined)
        if (selected && (!['image/png', 'image/jpeg', 'image/webp'].includes(selected.type) || selected.size > 1024 * 1024)) { setError('Choose a PNG, JPEG, or WebP up to 1 MiB.'); event.target.value = ''; return }
        setFile(selected)
      }} /></label>
      <div className="inline-actions"><Button size="sm" disabled={!file || save.isPending} onClick={() => save.mutate(false)}>Save {title.toLowerCase()}</Button><Button size="sm" variant="ghost" disabled={!customized || save.isPending} onClick={() => save.mutate(true)}>{kind === 'LOGO' ? 'Restore placeholder' : 'Use logo'}</Button></div>
      {(error || save.isError) && <p role="alert" className="feedback error">{error || getErrorMessage(save.error)}</p>}
      {save.isSuccess && <p role="status">{title} updated.</p>}
    </div>
  </section>
}
