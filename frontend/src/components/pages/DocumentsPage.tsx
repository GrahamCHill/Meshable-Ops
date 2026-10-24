import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { documentsApi, saveBlob, type GeneratedDocument } from '../../api/documents'
import { useAuthStore } from '../../hooks/useAuthStore'
import { useDocumentAccess } from '../../api/documentAccess'
import { DocumentDataForm } from '../documents/DocumentDataForm'
import { GeneratedPdfViewer } from '../documents/GeneratedPdfViewer'
import { EmailDocumentDialog } from '../documents/EmailDocumentDialog'
import { getErrorMessage } from '../admin/adminUtils'
import { Button, Card, Spinner } from '../ui'

export default function DocumentsPage() {
  const client = useQueryClient(); const [params, setParams] = useSearchParams()
  const admin = useAuthStore(state => state.user?.role === 'ADMIN')
  const templates = useQuery({ queryKey: [admin ? 'admin-document-templates' : 'document-templates'], queryFn: admin ? documentsApi.adminTemplates : documentsApi.templates })
  const documents = useQuery({ queryKey: ['generated-documents'], queryFn: documentsApi.list })
  const policy = useDocumentAccess()
  const selected = templates.data?.find(template => template.id === params.get('template'))
  const [preview, setPreview] = useState<GeneratedDocument>(); const [email, setEmail] = useState<GeneratedDocument>()
  const [error, setError] = useState(''); const [busy, setBusy] = useState('')
  const download = async (document: GeneratedDocument) => { setBusy(document.id); setError(''); try { saveBlob(await documentsApi.download(document.id), document.filename) } catch (failure) { setError(getErrorMessage(failure, 'Unable to download the PDF.')) } finally { setBusy('') } }
  return <div className="page-stack" style={{ maxWidth: 1100 }}>
    <div className="page-heading"><div><h1>Documents</h1><p>Create PDFs from your published templates.</p></div><div className="inline-actions"><Link to="/workflows" className="button button-secondary button-sm">Workflows</Link>{admin && <Link to="/admin/document-templates" className="button button-ghost button-sm">Manage templates</Link>}</div></div>
    {templates.isLoading && <Spinner />}{templates.isError && <p role="alert">Unable to load document templates.</p>}
    <div className="form-field"><label htmlFor="document-template">Create a document</label><select id="document-template" value={selected?.id ?? ''} onChange={e => { setError(''); setParams(e.target.value ? { template: e.target.value } : {}) }}><option value="">Choose a template</option>{templates.data?.map(template => <option key={template.id} value={template.id}>{template.name}{template.published ? '' : ' (admin draft)'}</option>)}</select></div>
    {templates.data?.length === 0 && <p>{admin ? 'Create and publish a template in Admin to get started.' : 'No templates have been published yet.'}</p>}
    {selected && <Card><DocumentDataForm key={`${selected.id}-${selected.revision}`} template={selected} onGenerated={document => { setPreview(document); void client.invalidateQueries({ queryKey: ['generated-documents'] }) }} /></Card>}
    {error && <p role="alert" className="feedback error">{error}</p>}
    <h2>Generated documents</h2><p className="field-help">Latest 100 PDFs available to your account. Use these PDFs in a workflow when you reach a document or email step. Each PDF keeps the template revision and values used to generate it.</p>
    {documents.isLoading && <Spinner />}{documents.isError && <p role="alert">Unable to load generated documents.</p>}{documents.data?.length === 0 && <p>No generated documents yet.</p>}
    {documents.data?.map(document => <Card key={document.id}><div className="inline-actions"><div style={{ flex: 1 }}><h3>{document.name}</h3><small>{document.templateName} · revision {document.templateRevision} · {document.pageCount} {document.pageCount === 1 ? 'page' : 'pages'} · {new Date(document.createdAt).toLocaleString()}</small></div>{policy.data?.pdfPreviewEnabled && <Button variant="ghost" onClick={() => setPreview(document)}>Open PDF</Button>}{policy.data && policy.data.downloadMode !== 'DISABLED' && <><Button variant="ghost" disabled={!!busy} onClick={() => void download(document)}>Download PDF</Button><Button variant="secondary" onClick={() => setEmail(document)}>Email PDF</Button></>}<Link to={`${admin ? '/admin/workflows' : '/workflows'}?attach=${document.id}`} className="button button-ghost button-sm">{admin ? 'Attach to workflow design' : 'Use in workflow'}</Link></div></Card>)}
    {preview && policy.data?.pdfPreviewEnabled && <GeneratedPdfViewer document={preview} onClose={() => setPreview(undefined)} />}
    {email && policy.data && policy.data.downloadMode !== 'DISABLED' && <EmailDocumentDialog document={email} onClose={() => setEmail(undefined)} />}
  </div>
}
