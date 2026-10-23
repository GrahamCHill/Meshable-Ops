import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { documentAccessApi, type DocumentAccessPolicy } from '../../api/documentAccess'
import { AdminPageHeader } from '../admin/AdminPageHeader'
import { getErrorMessage } from '../admin/adminUtils'
import { Button, Spinner } from '../ui'

export function AdminDocumentAccessPage() {
  const client = useQueryClient()
  const query = useQuery({ queryKey: ['admin-document-access'], queryFn: documentAccessApi.settings })
  const [draft, setDraft] = useState<DocumentAccessPolicy>()
  const [dirty, setDirty] = useState(false)
  useEffect(() => { if (query.data && !dirty) setDraft(query.data) }, [query.data, dirty])
  const save = useMutation({ mutationFn: documentAccessApi.save, onSuccess: policy => {
    client.setQueryData(['admin-document-access'], policy)
    client.setQueryData(['document-access'], policy)
    setDraft(policy); setDirty(false)
  } })
  const change = (patch: Partial<DocumentAccessPolicy>) => { setDraft(previous => previous && { ...previous, ...patch }); setDirty(true); save.reset() }
  return <div className="page-stack" style={{ maxWidth: 800 }}>
    <AdminPageHeader title="Document access" description="Control PDF viewing and downloads for everyone, including administrators." />
    {query.isLoading && <Spinner />}
    {query.isError && <p role="alert" className="feedback error">Unable to load document settings. <Button variant="ghost" onClick={() => query.refetch()}>Retry</Button></p>}
    {draft && <form className="page-stack" onSubmit={event => { event.preventDefault(); save.mutate(draft) }}>
      <fieldset className="page-stack" disabled={save.isPending}>
        <label className="checkbox-label"><input type="checkbox" checked={draft.pdfPreviewEnabled} onChange={event => change({ pdfPreviewEnabled: event.target.checked })} /> Allow PDFs to open in Storage</label>
        <div className="form-field"><label htmlFor="document-download-mode">Downloads</label><select id="document-download-mode" value={draft.downloadMode} onChange={event => change({ downloadMode: event.target.value as DocumentAccessPolicy['downloadMode'] })}>
          <option value="NORMAL">Download normally</option><option value="WATERMARKED">Download with watermarks</option><option value="DISABLED">Downloads disabled</option>
        </select></div>
        {draft.downloadMode === 'WATERMARKED' && <><div className="form-field"><label htmlFor="document-watermark">Watermark text</label><input id="document-watermark" required maxLength={100} value={draft.watermarkText} onChange={event => change({ watermarkText: event.target.value })} /></div><p className="field-help">Every PDF page includes this text, the signed-in user, and the UTC time. Other formats download normally. Folder ZIPs contain watermarked PDFs and unchanged files of other formats. The original stored file stays unchanged.</p></>}
        <p className="field-help">PDFs open as page images. Disabling downloads blocks original files and folder ZIPs on the server. People who can view a page can still capture its image.</p>
        <p className="field-help">Previewing and watermarking support unencrypted PDFs up to 25 MB and 200 pages. Watermarked copies may invalidate digital signatures.</p>
      </fieldset>
      {save.isError && <p role="alert" className="feedback error">{getErrorMessage(save.error)}</p>}
      {save.isSuccess && <p role="status" className="feedback success">Document access settings saved.</p>}
      <div className="save-bar"><span>{dirty ? 'You have unsaved changes' : 'Settings are up to date'}</span><div className="inline-actions"><Button type="button" variant="ghost" disabled={!dirty || save.isPending} onClick={() => { setDraft(query.data); setDirty(false); save.reset() }}>Discard changes</Button><Button type="submit" disabled={!dirty || !draft.watermarkText.trim()} loading={save.isPending}>Save document settings</Button></div></div>
    </form>}
  </div>
}
