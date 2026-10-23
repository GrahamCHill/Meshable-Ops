import { applySupersetTheme } from '../supersetTheme'
import { hideEmbedActions } from './embedActions'
import { constrainIframeNavigation } from './embedNavigation'

export function applyEmbedTheme(iframe: HTMLIFrameElement | null | undefined, mode: 'dark' | 'light') {
  if (!iframe) return

  let doc: Document | null = null
  try {
    doc = iframe.contentDocument
  } catch {
    return
  }
  if (!doc) return

  applySupersetTheme(doc, mode)
}

export function syncEmbedFrame(iframe: HTMLIFrameElement | null | undefined, mode: 'dark' | 'light') {
  applyEmbedTheme(iframe, mode)
  hideEmbedActions(iframe)
  constrainIframeNavigation(iframe)
}

export function stretchIframe(mount: HTMLDivElement | null, mode: 'dark' | 'light') {
  if (!mount) return

  const iframe = mount.querySelector('iframe')
  if (!iframe) return

  mount.style.width = '100%'
  mount.style.height = '100%'
  mount.style.minHeight = '100%'
  mount.style.display = 'block'

  iframe.style.width = '100%'
  iframe.style.height = '100%'
  iframe.style.minHeight = '100%'
  iframe.style.display = 'block'
  iframe.style.border = '0'
  iframe.setAttribute('width', '100%')
  iframe.setAttribute('height', '100%')

  let node = iframe.parentElement
  while (node && node !== mount) {
    node.style.width = '100%'
    node.style.height = '100%'
    node.style.minHeight = '100%'
    if (!node.style.display || node.style.display === 'inline') {
      node.style.display = 'block'
    }
    node = node.parentElement
  }

  applyEmbedTheme(iframe, mode)
  hideEmbedActions(iframe)
  constrainIframeNavigation(iframe)
}
