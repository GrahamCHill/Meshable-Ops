export function constrainIframeNavigation(iframe: HTMLIFrameElement | null | undefined) {
  if (!iframe) return

  let doc: Document | null = null
  let frameWindow: Window | null = null
  try {
    doc = iframe.contentDocument
    frameWindow = iframe.contentWindow
  } catch {
    return
  }
  if (!doc || !frameWindow) return

  const navHookKey = '__supersetNavigationHooked__'
  if ((frameWindow as Window & { [navHookKey]?: boolean })[navHookKey]) {
    doc.querySelectorAll<HTMLAnchorElement>('a[target]').forEach((anchor) => {
      anchor.target = '_self'
      anchor.rel = 'noopener noreferrer'
    })
    return
  }

  ; (frameWindow as Window & { [navHookKey]?: boolean })[navHookKey] = true

  try {
    frameWindow.open = ((url?: string | URL) => {
      if (typeof url === 'string' && url) {
        frameWindow!.location.assign(url)
      } else if (url instanceof URL) {
        frameWindow!.location.assign(url.toString())
      }
      return frameWindow
    }) as typeof window.open
  } catch {
    // Ignore if the browser prevents overriding open on the iframe window.
  }

  doc.querySelectorAll<HTMLAnchorElement>('a[target]').forEach((anchor) => {
    anchor.target = '_self'
    anchor.rel = 'noopener noreferrer'
  })

  doc.addEventListener('click', (event) => {
    const target = event.target
    if (!target || (target as Element).nodeType !== 1) return
    const anchor = (target as Element).closest('a[href]') as HTMLAnchorElement | null
    if (!anchor) return
    if (anchor.target && anchor.target !== '_self') {
      anchor.target = '_self'
    }
  }, true)
}
