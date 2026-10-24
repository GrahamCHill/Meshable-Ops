import { useEffect, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { brandingApi, defaultBrandingSettings, type Branding, type BrandingSettings } from '../../../api/branding'
import { WorkspaceThemeEditor } from './WorkspaceThemeEditor'
import { getErrorMessage } from '../adminUtils'
import { Button } from '../../ui'

export function BrandingAppearanceForm({ branding }: { branding: Branding }) {
  const settings = branding.settings ?? defaultBrandingSettings
  const images = branding.loginImages ?? []
  const client = useQueryClient()
  const [draft, setDraft] = useState(settings)
  const [dirty, setDirty] = useState(false)
  const save = useMutation({ mutationFn: () => brandingApi.save(draft), onSuccess: data => {
    client.setQueryData(['branding'], data); setDraft(data.settings); setDirty(false)
  } })
  useEffect(() => { if (!dirty) setDraft(settings) }, [settings, dirty])
  const update = (patch: Partial<BrandingSettings>) => { setDraft(current => ({ ...current, ...patch })); setDirty(true); save.reset() }
  const missingSelected = draft.loginBackgroundSlot !== null && !images.some(image => image.slot === draft.loginBackgroundSlot)
  return <form className="page-stack" onSubmit={event => { event.preventDefault(); save.mutate() }}>
    <fieldset className="page-stack" disabled={save.isPending}>
    <h2>Business identity</h2>
    <label className="form-field">Application name<input required maxLength={80} value={draft.applicationName} onChange={event => update({ applicationName: event.target.value })} /></label>
    <p className="field-help">Used in navigation, on sign-in, and as the browser tab title.</p>
    <WorkspaceThemeEditor value={draft.themes} onChange={themes => update({ themes, sidebarBackgroundColor: null, sidebarTextColor: null, linkColor: null })}/>
    <h2>Sign-in background</h2>
    <label className="form-field">Background display<select value={draft.loginBackgroundMode} onChange={event => update({ loginBackgroundMode: event.target.value as BrandingSettings['loginBackgroundMode'] })}><option value="NONE">No image</option><option value="SINGLE">Single image</option><option value="SLIDESHOW">Slideshow</option></select></label>
    {draft.loginBackgroundMode === 'SINGLE' && <label className="form-field">Background image<select value={draft.loginBackgroundSlot ?? ''} onChange={event => update({ loginBackgroundSlot: event.target.value === '' ? null : Number(event.target.value) })}><option value="">First available image</option>{images.map(image => <option key={image.slot} value={image.slot}>Image {image.slot + 1}</option>)}{missingSelected && <option value={draft.loginBackgroundSlot!}>Image {draft.loginBackgroundSlot! + 1} (removed)</option>}</select></label>}
    {draft.loginBackgroundMode === 'SLIDESHOW' && <label className="form-field">Seconds per image<input type="number" min={5} max={60} required value={draft.slideshowSeconds} onChange={event => update({ slideshowSeconds: Number(event.target.value) })} /></label>}
    <p className="field-help">Upload images in Sign-in images below, then save a display mode. The logo appears above the sign-in form. Slideshows respect reduced-motion preferences and include pause and previous/next controls. One available image stays still.</p>
    {draft.loginBackgroundMode !== 'NONE' && images.length === 0 && <p role="status">Upload an image before enabling a sign-in background.</p>}
    {draft.revision !== settings.revision && dirty && <p role="alert">Saved branding changed while you were editing. Reload saved values before saving.</p>}
    <div className="inline-actions"><Button type="submit" disabled={!dirty || save.isPending || (draft.loginBackgroundMode !== 'NONE' && !images.length)}>Save branding settings</Button><Button type="button" variant="ghost" disabled={save.isPending} onClick={() => { setDraft(settings); setDirty(false); save.reset() }}>Reload saved values</Button><Button type="button" variant="ghost" disabled={save.isPending} onClick={() => update({ ...defaultBrandingSettings, revision: draft.revision })}>Use default settings</Button></div>
    {save.isError && <p role="alert" className="feedback error">{getErrorMessage(save.error)}</p>}
    {save.isSuccess && <p role="status">Branding settings saved.</p>}
    </fieldset>
  </form>
}
