import type {ReactNode} from 'react'
import {useModalDialog} from '../../hooks/useModalDialog'

export function Modal({title, onClose, children, busy = false, className = 'project-dialog'}: {title: string; onClose: () => void; children: ReactNode; busy?: boolean; className?: string}) {
  const dialog = useModalDialog(onClose, busy)
  return <dialog ref={dialog} className={className} aria-label={title} onCancel={event => {event.preventDefault(); if (!busy) onClose()}}>
    <div className="page-heading"><h2>{title}</h2><button type="button" disabled={busy} onClick={onClose} aria-label={`Close ${title}`}>Close</button></div>{children}
  </dialog>
}
