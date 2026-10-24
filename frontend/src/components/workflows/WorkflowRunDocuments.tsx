import { useCallback, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { documentsApi, saveBlob } from '../../api/documents'
import { useDocumentAccess } from '../../api/documentAccess'
import { workflowRunsApi, type WorkflowRun } from '../../api/workflows'
import { useAuthStore } from '../../hooks/useAuthStore'
import { GeneratedPdfViewer } from '../documents/GeneratedPdfViewer'
import { getErrorMessage } from '../admin/adminUtils'
import type { RunAction } from './WorkflowRunPanel'
import { Button } from '../ui'

export function WorkflowRunDocuments({ run, suggestedDocument, busy, act }: { run: WorkflowRun; suggestedDocument?: string; busy: boolean; act: RunAction }) {
  const user = useAuthStore(state => state.user)
  const own = useQuery({ queryKey: ['workflow-own-documents', user?.id], queryFn: documentsApi.list })
  const policy = useDocumentAccess(); const [preview, setPreview] = useState(false)
  const [selected, setSelected] = useState(suggestedDocument ?? '')
  const [error, setError] = useState(''); const [downloading, setDownloading] = useState(false)
  const document = run.documents.find(value => value.id === run.current?.documentId)
  const loadPage = useCallback((page: number, signal: AbortSignal) => workflowRunsApi.page(run.id, document!.id, page, signal), [run.id, document?.id])
  const download = async () => { if (!document) return; setError(''); setDownloading(true); try { saveBlob(await workflowRunsApi.download(run.id, document.id), document.filename) } catch (failure) { setError(getErrorMessage(failure, 'Unable to download this PDF.')) } finally { setDownloading(false) } }
  return <div className="page-stack">
    {document ? <div><p><strong>PDF:</strong> {document.name} · {document.pageCount} {document.pageCount === 1 ? 'page' : 'pages'}</p><div className="inline-actions">{policy.data?.pdfPreviewEnabled && <Button variant="secondary" disabled={busy} onClick={() => setPreview(true)}>Open PDF</Button>}{policy.data && policy.data.downloadMode !== 'DISABLED' && <Button variant="ghost" disabled={busy || downloading} onClick={() => void download()}>Download PDF</Button>}</div></div> : <p role="alert">The PDF for this step is unavailable. Select a PDF from your library or contact an administrator.</p>}
    {error && <p role="alert">{error}</p>}
    <details><summary>Use a PDF from your document library</summary><p className="field-help">The selected PDF replaces this document in this run, including later steps that use the same document.</p>{own.isError && <p role="alert">Unable to load your PDF library.</p>}<div className="form-field"><label htmlFor="workflow-pdf">Your generated PDF</label><select id="workflow-pdf" disabled={busy || run.emailState !== 'NONE'} value={selected} onChange={e => setSelected(e.target.value)}><option value="">Choose a PDF</option>{own.data?.map(value => <option key={value.id} value={value.id}>{value.name}</option>)}</select></div><Button variant="secondary" disabled={busy || run.emailState !== 'NONE' || !own.data?.some(value => value.id === selected)} onClick={() => void act(() => workflowRunsApi.useDocument(run.id, run.revision, run.current!.id, selected))}>Use this PDF</Button></details>
    {preview && document && policy.data?.pdfPreviewEnabled && <GeneratedPdfViewer document={document} loadPage={loadPage} onClose={() => setPreview(false)} />}
  </div>
}
