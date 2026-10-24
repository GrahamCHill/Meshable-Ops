import { useEffect, useState } from 'react'
import { documentsApi, type AppTemplate, type GeneratedDocument } from '../api'
import { getErrorMessage } from '../../../../components/admin/adminUtils'
import { Button } from '../../../../components/ui/index'

export function DocumentDataForm({ template, onGenerated }: { template: AppTemplate; onGenerated: (document: GeneratedDocument) => void }) {
  const [name, setName] = useState(template.name)
  const [values, setValues] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false); const [error, setError] = useState('')
  useEffect(() => {
    const now = new Date(); const today = `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}-${String(now.getDate()).padStart(2,'0')}`
    setName(template.name); setValues(Object.fromEntries((template.template.apiFields ?? []).map(field => [field.key, field.fallback || (template.fieldRules.find(rule => rule.key === field.key)?.type === 'DATE' ? today : '')]))); setError('')
  }, [template])
  const submit = async () => {
    setBusy(true); setError('')
    try { onGenerated(await documentsApi.generate(template.id, name, values)) }
    catch (failure) { setError(getErrorMessage(failure, 'Unable to generate this document.')) }
    finally { setBusy(false) }
  }
  return <form className="page-stack" onSubmit={e => { e.preventDefault(); void submit() }}>
    <h2>Create from {template.name}</h2>
    <fieldset className="page-stack" disabled={busy}>
      <div className="form-field"><label htmlFor="generated-name">Document name</label><input id="generated-name" required maxLength={120} value={name} onChange={e => setName(e.target.value)} /></div>
      <div className="form-grid">{(template.template.apiFields ?? []).map(field => {
        const rule = template.fieldRules.find(rule => rule.key === field.key); const type = rule?.type ?? 'TEXT'; const id = `document-field-${field.key}`
        return <div key={field.key} className="form-field"><label htmlFor={id}>{field.label}{field.required ? ' *' : ''}</label>{type === 'DERIVED' ? <p id={id} className="field-help">Calculated on generation: {rule?.operation?.toLowerCase()} ({rule?.inputs.join(', ')})</p> : type === 'MULTILINE' ? <textarea id={id} rows={5} required={field.required} maxLength={field.maxLength} value={values[field.key] ?? ''} onChange={e => setValues({ ...values, [field.key]: e.target.value })} /> : <input id={id} required={field.required} type={type === 'DATE' ? 'date' : type === 'EMAIL' ? 'email' : type === 'NUMBER' || type === 'MONEY' ? 'number' : 'text'} step={type === 'NUMBER' || type === 'MONEY' ? 'any' : undefined} maxLength={field.maxLength} value={values[field.key] ?? ''} onChange={e => setValues({ ...values, [field.key]: e.target.value })} />}</div>
      })}</div>
    </fieldset>
    {error && <p role="alert" className="feedback error">{error}</p>}
    <p className="field-help">A PDF and a snapshot of this template revision are saved. Fields and layout come from the selected template. Calculations are validated on the server.</p>
    <div><Button type="submit" loading={busy}>Generate PDF</Button></div>
  </form>
}
