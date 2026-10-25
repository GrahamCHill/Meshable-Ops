import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAccess } from '../../api/access'
import { aiApi, type AiAnswer, type AiSearchStatus } from '../../api/ai'
import { searchApi, type SearchResponse } from '../../api/search'
import { getErrorMessage } from '../admin/adminUtils'
import { Button, Spinner } from '../ui'
import { AiAnswerPanel } from './AiAnswerPanel'
import { SearchResult } from './SearchResult'

interface Turn {
  id: number
  question: string
  mode: 'answer' | 'search'
  answer?: AiAnswer
  results?: SearchResponse
  error?: string
}

export function WorkspaceAssistant({ status, open, onNavigate }: { status: AiSearchStatus; open: boolean; onNavigate: () => void }) {
  const admin = useAccess().can('admin.ai')
  const [draft, setDraft] = useState('')
  const [turns, setTurns] = useState<Turn[]>([])
  const [busy, setBusy] = useState(false)
  const pending = useRef(false)
  const nextId = useRef(0)
  const transcript = useRef<HTMLDivElement>(null)
  const composer = useRef<HTMLTextAreaElement>(null)
  const askEnabled = status.enabled && status.indexContent

  useEffect(() => {
    if (open && transcript.current) transcript.current.scrollTop = transcript.current.scrollHeight
  }, [turns, busy, open])

  const send = async (question: string, retry?: Turn) => {
    question = question.trim()
    if (!question || pending.current) return
    pending.current = true
    setBusy(true)
    const turn: Turn = { id: retry?.id ?? nextId.current++, question, mode: askEnabled ? 'answer' : 'search' }
    const history = turns.filter(item => item.id < turn.id && item.answer?.grounded).slice(-6).map(item => ({ query: item.question }))
    setTurns(current => retry ? current.map(item => item.id === turn.id ? turn : item) : [...current, turn])
    if (!retry) setDraft('')
    composer.current?.focus()
    try {
      const result = turn.mode === 'answer'
        ? { answer: await aiApi.ask(question, 5, history) }
        : { results: await searchApi.search(question, 5) }
      setTurns(current => current.map(item => item.id === turn.id ? { ...item, ...result } : item))
    } catch (error) {
      setTurns(current => current.map(item => item.id === turn.id ? { ...item, error: getErrorMessage(error, 'I couldn’t complete that request. Please try again.') } : item))
    } finally {
      pending.current = false
      setBusy(false)
    }
  }

  const suggestions = askEnabled
    ? ['Which contracts renew this quarter?', 'What are the cancellation terms?']
    : ['Contracts', 'Project reports']

  return <div className="assistant-chat">
    <div className="assistant-chat-toolbar"><span>{askEnabled ? 'Answers from your documents' : 'Document search available'}</span><button className="utility-button" type="button" disabled={busy || !turns.length} onClick={() => { setTurns([]); setDraft(''); composer.current?.focus() }}>New chat</button></div>
    <div ref={transcript} className="assistant-transcript" role="log" aria-label="Conversation with workspace assistant" aria-live="polite" aria-relevant="additions text">
      <div className="assistant-message assistant-message-reply">
        <span className="assistant-message-author">Workspace assistant</span>
        <p>Hi! How can I help?</p>
        <p className="field-help">{askEnabled ? 'Ask a question about your documents, then ask a follow-up. I’ll include sources you can open.' : 'I can help you find folders and documents. AI answers will be available once your administrator enables them and document content indexing.'}</p>
      </div>
      {!turns.length && <div className="assistant-suggestions" aria-label="Suggested messages">{suggestions.map(question => <button type="button" key={question} onClick={() => void send(question)}>{question}</button>)}</div>}
      {turns.map(turn => <div key={turn.id} className="assistant-turn">
        <div className="assistant-message assistant-message-user"><span className="assistant-message-author">You</span><p>{turn.question}</p></div>
        <div className="assistant-message assistant-message-reply">
          <span className="assistant-message-author">Workspace assistant</span>
          {turn.answer && <AiAnswerPanel answer={turn.answer} onNavigate={onNavigate} conversational />}
          {turn.results && <div className="page-stack"><p>{turn.results.results.length ? `Here’s what I found for “${turn.question}”.` : 'I couldn’t find a match. Try a document name or a different phrase.'}</p>{turn.results.notice && <p className="field-help">{turn.results.notice}</p>}{turn.results.results.map((result, index) => <SearchResult key={result.pointId} result={result} rank={index + 1} keyword={turn.results?.mode === 'KEYWORD'} onNavigate={onNavigate} />)}</div>}
          {turn.error && <div role="alert"><p>{turn.error}</p><button className="utility-button" type="button" disabled={busy} onClick={() => void send(turn.question, turn)}>Try again</button></div>}
          {!turn.answer && !turn.results && !turn.error && <p role="status"><Spinner /> {turn.mode === 'answer' ? 'Reading your documents…' : 'Finding documents…'}</p>}
        </div>
      </div>)}
    </div>
    <form className="assistant-composer" onSubmit={event => { event.preventDefault(); void send(draft) }}>
      <label htmlFor="assistant-message">{turns.length ? 'Reply to assistant' : 'Message workspace assistant'}</label>
      <div className="assistant-composer-input"><textarea ref={composer} id="assistant-message" rows={2} maxLength={4000} placeholder={askEnabled ? 'Ask a question…' : 'Describe the document you need…'} value={draft} onChange={event => setDraft(event.target.value)} onKeyDown={event => { if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) { event.preventDefault(); event.currentTarget.form?.requestSubmit() } }} /><Button type="submit" disabled={busy || !draft.trim()}>Send</Button></div>
      <div className="assistant-composer-help"><span>Enter to send · Shift+Enter for a new line</span><Link to="/search" onClick={onNavigate}>Search workspace</Link>{admin && <Link to="/admin/ai" onClick={onNavigate}>AI settings</Link>}</div>
    </form>
  </div>
}
