import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { sharesApi, type SharedFile } from '../../api/shares'
import { getErrorMessage } from '../admin/adminUtils'
import { Button, Spinner } from '../ui'

function SharedPageImage({ token, grant, page }: { token: string; grant: string; page: number }) {
  const [url, setUrl] = useState(''); const [error, setError] = useState(false)
  useEffect(() => {
    const controller = new AbortController(); let image = ''
    void sharesApi.page(token, grant, page, controller.signal).then(blob => { if (!controller.signal.aborted) { image = URL.createObjectURL(blob); setUrl(image) } }).catch(() => { if (!controller.signal.aborted) setError(true) })
    return () => { controller.abort(); if (image) URL.revokeObjectURL(image) }
  }, [token, grant, page])
  return error ? <p role="alert">This page is unavailable. Unlock the link again or request a new share.</p> : url ? <img className="pdf-page" src={url} alt={`Shared PDF page ${page}`} /> : <Spinner />
}
export function SharedFilePage() {
  const { token = '' } = useParams()
  const [password, setPassword] = useState(''); const [grant, setGrant] = useState('')
  const [file, setFile] = useState<SharedFile>(); const [page, setPage] = useState(1)
  const [busy, setBusy] = useState(false); const [error, setError] = useState('')
  useEffect(() => {
    const previous = document.querySelector<HTMLMetaElement>('meta[name="referrer"]'); const content = previous?.content
    const meta = previous ?? document.createElement('meta'); meta.name = 'referrer'; meta.content = 'no-referrer'; if (!previous) document.head.append(meta)
    return () => { if (previous) previous.content = content ?? ''; else meta.remove() }
  }, [])
  const unlock = async () => {
    setBusy(true); setError('')
    try { const session = await sharesApi.unlock(token, password); const metadata = await sharesApi.metadata(token, session.accessToken); setGrant(session.accessToken); setFile(metadata) }
    catch (failure) { setError(getErrorMessage(failure, 'This file is unavailable or the password is incorrect.')) }
    finally { setBusy(false) }
  }
  const download = async () => {
    setBusy(true); setError('')
    try { const blob = await sharesApi.download(token, grant, password); const url = URL.createObjectURL(blob); const anchor = document.createElement('a'); anchor.href = url; anchor.download = file!.filename; anchor.click(); setTimeout(() => URL.revokeObjectURL(url), 1000) }
    catch { setError('Download unavailable. Unlock again or contact the sender.') }
    finally { setBusy(false) }
  }
  return <main className="page-stack" style={{ maxWidth: 1000, margin: '3rem auto', padding: '0 1.5rem' }}>
    <h1>{file?.filename ?? 'Protected PDF'}</h1>
    {!file ? <form className="page-stack" style={{ maxWidth: 500 }} onSubmit={e => { e.preventDefault(); void unlock() }}><p>Enter the password provided by the sender to view this file. The password is also required to open the PDF attachment in your email.</p><div className="form-field"><label htmlFor="shared-password">File password</label><input id="shared-password" type="password" autoComplete="off" maxLength={72} required value={password} onChange={e => setPassword(e.target.value)} /></div><div><Button type="submit" loading={busy}>Unlock PDF</Button></div></form> : <>
      <p>Shared with {file.recipient} · Online link expires {new Date(file.expiresAt).toLocaleString()}</p>
      <div className="inline-actions">{file.canDownload && <Button loading={busy} onClick={() => void download()}>Download protected PDF</Button>}<Button variant="ghost" disabled={busy} onClick={() => { setFile(undefined); setGrant(''); setPassword(''); setPage(1); setError('') }}>Lock file</Button></div>
      <p className="field-help">Shared copies are watermarked. Downloaded PDFs require the same password when opened.</p>
      {file.canPreview ? <><nav className="inline-actions" aria-label="PDF pages"><Button variant="ghost" disabled={page <= 1} onClick={() => setPage(page - 1)}>Previous page</Button><span>Page {page} of {file.pageCount}</span><Button variant="ghost" disabled={page >= file.pageCount} onClick={() => setPage(page + 1)}>Next page</Button></nav><div className="pdf-canvas"><SharedPageImage key={page} token={token} grant={grant} page={page} /></div></> : <p>Online PDF viewing is disabled.</p>}
    </>}
    {error && <p role="alert" className="feedback error">{error}</p>}
  </main>
}
