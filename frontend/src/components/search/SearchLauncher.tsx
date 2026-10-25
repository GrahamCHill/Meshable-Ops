import { useAccess } from '../../api/access'
import { useEffect, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { aiApi } from '../../api/ai'
import { WorkspaceAssistant } from './WorkspaceAssistant'

export function SearchLauncher() {
  const access = useAccess()
  const status = useQuery({ queryKey: ['ai-search-status'], queryFn: aiApi.status, refetchInterval: 30000, enabled: access.can('search') })
  const [open, setOpen] = useState(false)
  const [opened, setOpened] = useState(false)
  const button = useRef<HTMLButtonElement>(null)
  const input = useRef<HTMLElement>(null)
  const enabled = access.can('search') && status.data?.searchEnabled && status.data.launcherEnabled
  const close = () => { setOpen(false); button.current?.focus() }
  useEffect(() => { if (!enabled) setOpen(false) }, [enabled])
  useEffect(() => { if (open) input.current?.querySelector('textarea')?.focus() }, [open])
  useEffect(() => {
    if (!open) return
    const escape = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && event.target instanceof Node && input.current?.contains(event.target)) {
        event.stopPropagation(); setOpen(false); button.current?.focus()
      }
    }
    document.addEventListener('keydown', escape)
    return () => document.removeEventListener('keydown', escape)
  }, [open])
  if (!enabled) return null
  return <>
    {opened && <aside id="workspace-assistant" ref={input} hidden={!open} role="dialog" aria-modal="false" aria-label="AI assistant" className="search-assistant">
      <div className="search-assistant-heading"><h2>Workspace assistant</h2><button className="utility-button" type="button" onClick={close}>Close</button></div>
      {status.data && <WorkspaceAssistant status={status.data} open={open} onNavigate={close} />}
    </aside>}
    <button ref={button} className="search-launcher" type="button" aria-label={open ? 'Close AI assistant' : 'Open AI assistant'} title="Chat with your workspace assistant" aria-expanded={open} aria-controls="workspace-assistant" onClick={() => { setOpened(true); setOpen(current => !current) }}>
      <svg width="25" height="25" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" aria-hidden="true"><path d="M20 11.5a8 8 0 0 1-8 8H8l-5 2v-6a8 8 0 1 1 17-4Z" /><path d="M7 10h9M7 14h6" /></svg>
    </button>
  </>
}
