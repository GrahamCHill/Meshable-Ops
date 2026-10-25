// SPDX-License-Identifier: LicenseRef-Meshable-Ops-Community-1.1
import React, {useState} from 'react'
import {createRoot} from 'react-dom/client'

declare const meshableOps: {backend: {request: (path: string, body?: unknown, method?: string) => Promise<any>}}
function App() {
  const [title, setTitle] = useState(''), [output, setOutput] = useState(''), [busy, setBusy] = useState(false)
  async function run(path: string, method = 'GET') {
    setBusy(true)
    try { setOutput(JSON.stringify(await meshableOps.backend.request(path, method === 'POST' ? {title} : null, method), null, 2)) }
    catch (error) { setOutput(error instanceof Error ? error.message : 'Request failed') }
    finally { setBusy(false) }
  }
  return <main><h1>Uploaded frontend and backend</h1>
    <p>This React bundle calls Python for notes and Kotlin for a summary. Both modules came from this ZIP.</p>
    <form onSubmit={e => {e.preventDefault(); void run('/notes', 'POST')}}>
      <label>Note title <input required maxLength={100} value={title} onChange={e => setTitle(e.target.value)}/></label>
      <button disabled={busy}>Save using Python</button>
    </form>
    <button disabled={busy} onClick={() => void run('/notes')}>List using Python</button>{' '}
    <button disabled={busy} onClick={() => void run('/summary')}>Summarize using Kotlin</button>
    <pre role="status">{output}</pre>
  </main>
}
createRoot(document.getElementById('root')!).render(<App/> )
