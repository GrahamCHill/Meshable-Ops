import { useEffect, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { aiApi } from '../../api/ai'
import { SearchPanel } from './SearchPanel'

export function SearchLauncher() {
  const status = useQuery({ queryKey: ['ai-search-status'], queryFn: aiApi.status, refetchInterval: 30000 })
  const [open, setOpen] = useState(false)
  const [opened, setOpened] = useState(false)
  const button = useRef<HTMLButtonElement>(null)
  const input = useRef<HTMLElement>(null)
  const enabled = status.data?.enabled && status.data.indexContent && status.data.launcherEnabled
  const close = () => { setOpen(false); button.current?.focus() }
  useEffect(() => { if (!enabled) setOpen(false) }, [enabled])
  useEffect(() => { if (open) input.current?.querySelector('input')?.focus() }, [open])
  if (!enabled) return null
  return <>
    {opened && <aside ref={input} hidden={!open} role="dialog" aria-modal="false" aria-label="AI assistant" className="search-assistant" onKeyDown={event => { if (event.key === 'Escape') { event.stopPropagation(); close() } }}>
      <div className="search-assistant-heading"><h2>Ask your documents</h2><button className="utility-button" type="button" onClick={close}>Close</button></div>
      <div className="search-assistant-content"><SearchPanel compact onNavigate={close} /></div>
    </aside>}
    <button ref={button} className="search-launcher" type="button" aria-label={open ? 'Close AI assistant' : 'Open AI assistant'} title="Ask your documents" aria-expanded={open} onClick={() => { setOpened(true); setOpen(!open) }}>
      <svg width="25" height="25" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" aria-hidden="true"><path d="M20 11.5a8 8 0 0 1-8 8H8l-5 2v-6a8 8 0 1 1 17-4Z" /><circle cx="10" cy="10" r="3" /><path d="m12.2 12.2 3 3" /></svg>
    </button>
  </>
}
