import { useState } from 'react'

export function ServiceAccountJsonField({ id, value, onChange, configured, required = false }: {
  id: string; value: string; onChange: (value: string) => void; configured: boolean; required?: boolean
}) {
  const [visible, setVisible] = useState(false)
  const props = { id, value, required, maxLength: 32768, autoComplete: 'new-password', placeholder: configured ? 'Configured; leave blank to retain' : 'Paste the service account JSON key', onChange: (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => onChange(event.target.value) }
  return <div className="form-field full-width">
    <label htmlFor={id}>Service account JSON key</label>
    {visible ? <textarea {...props} rows={8} spellCheck={false} /> : <input {...props} type="password" />}
    <div><button type="button" className="utility-button" aria-controls={id} aria-pressed={visible} onClick={() => setVisible(!visible)}>{visible ? 'Hide JSON key' : 'Show JSON key'}</button></div>
    <small>Use a Google service account key with access to this bucket. It is encrypted on the server and never returned to the browser. Leave blank to retain the saved key for the same location.</small>
  </div>
}
