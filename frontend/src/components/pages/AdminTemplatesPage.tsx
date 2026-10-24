import { useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { documentsApi, isFieldRules, type AppTemplate, type TemplateInput } from '../../api/documents'
import { TemplateDesigner } from '../../vendor/weasycanvas/src/TemplateDesigner'
import { isTemplate } from '../../vendor/weasycanvas/src/templates'
import type { PdfEditorApiValues } from '../../vendor/weasycanvas/editor'
import { DataFieldRules } from '../documents/DataFieldRules'
import { starterTemplate } from '../documents/starterTemplates'
import { AdminPageHeader } from '../admin/AdminPageHeader'
import { getErrorMessage } from '../admin/adminUtils'
import { Button, Spinner } from '../ui'
import '../../vendor/weasycanvas/editor/styles.css'
import '../../vendor/weasycanvas/designer.css'
import '../../styles/template-editor.css'

export default function AdminTemplatesPage() {
  const client = useQueryClient(); const navigate = useNavigate()
  const list = useQuery({ queryKey: ['admin-document-templates'], queryFn: documentsApi.adminTemplates })
  const [id, setId] = useState<string>(); const [draft, setDraft] = useState<TemplateInput>(() => starterTemplate('OTHER'))
  const [dirty, setDirty] = useState(true); const [busy, setBusy] = useState(false)
  const [error, setError] = useState(''); const [notice, setNotice] = useState('')
  const [previewValues, setPreviewValues] = useState<PdfEditorApiValues>({})
  const importInput = useRef<HTMLInputElement>(null)
  const change = (patch: Partial<TemplateInput>) => { setDraft(current => ({ ...current, ...patch })); setDirty(true); setNotice(''); setError('') }
  const load = (saved: AppTemplate) => { setId(saved.id); setDraft(saved); setPreviewValues({}); setDirty(false); setNotice(''); setError('') }
  const create = (category: TemplateInput['category']) => { setId(undefined); setDraft(starterTemplate(category)); setPreviewValues({}); setDirty(true); setNotice(''); setError('') }
  const save = async (use: boolean) => {
    setBusy(true); setError(''); setNotice('')
    try {
      const validKeys = new Set(draft.template.apiFields?.map(field => field.key))
      const saved = await documentsApi.saveTemplate(id, { ...draft, template: { ...draft.template, name: draft.name }, fieldRules: draft.fieldRules.filter(rule => validKeys.has(rule.key)) })
      load(saved); setNotice(saved.published ? 'Published template saved.' : 'Draft template saved. Publish it when users can generate documents.')
      await Promise.all([client.invalidateQueries({ queryKey: ['admin-document-templates'] }), client.invalidateQueries({ queryKey: ['document-templates'] })])
      if (use) navigate(`/documents?template=${saved.id}`)
    } catch (failure) { setError(getErrorMessage(failure, 'Unable to save template. Check the renderer and template fields.')) }
    finally { setBusy(false) }
  }
  const exportTemplate = () => { const blob = new Blob([JSON.stringify({ ...draft.template, hostFieldRules: draft.fieldRules }, null, 2)], { type: 'application/json' }); const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = 'document-template.json'; a.click(); setTimeout(() => URL.revokeObjectURL(url), 1000) }
  const importTemplate = async (file?: File) => {
    if (!file) return
    try {
      if (file.size > 1900000) throw new Error('Template files must be smaller than 1.9 MB.')
      const input = JSON.parse(await file.text()) as Record<string, unknown>
      const { hostFieldRules, ...template } = input
      if (!isTemplate(template)) throw new Error('Choose a valid WeasyCanvas template JSON file.')
      if (hostFieldRules !== undefined && !isFieldRules(hostFieldRules)) throw new Error('The template has invalid field types or calculation rules.')
      setId(undefined); setPreviewValues({}); setDraft({ name: template.name, category: 'OTHER', published: false, revision: 0, template, fieldRules: hostFieldRules ?? [] }); setDirty(true); setError('')
    } catch (failure) { setError(failure instanceof Error ? failure.message : 'Unable to import template.') }
  }
  return <div className="page-stack">
    <AdminPageHeader title="Document templates" description="Design reusable documents with WeasyCanvas, named data fields, and simple calculations." />
    <div className="inline-actions"><Button variant="secondary" disabled={busy} onClick={() => create('OTHER')}>New template</Button><Button variant="ghost" disabled={busy} onClick={() => create('QUOTE')}>Start with quote</Button><Button variant="ghost" disabled={busy} onClick={() => create('INVOICE')}>Start with invoice</Button><span>{dirty ? 'Unsaved changes' : 'Up to date'}</span></div>
    {list.isLoading && <Spinner />}{list.isError && <p role="alert">Unable to load templates.</p>}
    {!!list.data?.length && <div className="form-field" style={{ maxWidth: 560 }}><label htmlFor="saved-template">Saved templates (latest 100)</label><select id="saved-template" value={id ?? ''} disabled={busy} onChange={e => { const saved = list.data.find(item => item.id === e.target.value); if (saved) load(saved) }}><option value="">New unsaved template</option>{list.data.map(item => <option key={item.id} value={item.id}>{item.name} · {item.published ? 'Published' : 'Draft'}</option>)}</select></div>}
    <fieldset disabled={busy} className="page-stack">
      <div className="form-grid"><div className="form-field"><label htmlFor="template-name">Template name</label><input id="template-name" required maxLength={100} value={draft.name} onChange={e => change({ name: e.target.value, template: { ...draft.template, name: e.target.value } })} /></div><div className="form-field"><label htmlFor="template-category">Category</label><select id="template-category" value={draft.category} onChange={e => change({ category: e.target.value as TemplateInput['category'] })}><option value="OTHER">Other document</option><option value="QUOTE">Quote</option><option value="INVOICE">Invoice</option></select></div></div>
      <label className="checkbox-label"><input type="checkbox" checked={draft.published} onChange={e => change({ published: e.target.checked })} /> Publish for document creation</label>
      <DataFieldRules template={draft.template} rules={draft.fieldRules} onChange={fieldRules => change({ fieldRules })} />
      <div className="weasy-host"><TemplateDesigner key={id ?? draft.template.id} template={draft.template} apiValues={previewValues} onApiValuesChange={setPreviewValues} onChange={template => change({ template, name: template.name === draft.template.name ? draft.name : template.name })} onSave={() => void save(false)} onUse={() => void save(true)} onExport={exportTemplate} onImport={() => importInput.current?.click()} /></div>
    </fieldset>
    <input ref={importInput} type="file" accept="application/json,.json" hidden onChange={e => { void importTemplate(e.target.files?.[0]); e.target.value = '' }} />
    {error && <p role="alert" className="feedback error">{error}</p>}{notice && <p role="status">{notice}</p>}
    <p className="field-help">Named fields take values from the creation form or authenticated API; templates never fetch arbitrary URLs or execute formulas. Revisions are saved on the server. Required electronic signatures and automatic workflow execution will need a later integration.</p>
  </div>
}
