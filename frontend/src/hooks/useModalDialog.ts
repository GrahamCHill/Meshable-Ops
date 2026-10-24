import {useEffect, useRef} from 'react'

/** Backdrop dismissal requires both pointer-down and pointer-up outside, so selecting text does not close a dialog. */
export function useModalDialog(close: () => void, busy = false) {
  const dialog = useRef<HTMLDialogElement>(null)
  const latest = useRef({close, busy}); latest.current = {close, busy}
  useEffect(() => {
    const element = dialog.current
    if (!element) return
    element.showModal()
    let beganOutside = false
    const outside = (event: PointerEvent) => {
      const rect = element.getBoundingClientRect()
      return event.target === element && (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom)
    }
    const down = (event: PointerEvent) => {beganOutside = outside(event)}
    const up = (event: PointerEvent) => {if (beganOutside && outside(event) && !latest.current.busy) latest.current.close(); beganOutside = false}
    element.addEventListener('pointerdown', down); element.addEventListener('pointerup', up)
    return () => {element.removeEventListener('pointerdown', down); element.removeEventListener('pointerup', up); element.close()}
  }, [])
  return dialog
}
