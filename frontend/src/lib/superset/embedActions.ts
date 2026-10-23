export function hideEmbedActions(iframe: HTMLIFrameElement | null | undefined) {
  if (!iframe) return

  let doc: Document | null = null
  try {
    doc = iframe.contentDocument
  } catch {
    return
  }
  if (!doc) return

  const styleId = 'superset-embed-hide-actions'
  if (!doc.getElementById(styleId)) {
    const style = doc.createElement('style')
    style.id = styleId
    style.textContent = `
        [aria-label*="Share" i],
        [aria-label*="Save as" i],
        [aria-label*="Edit chart" i],
        [aria-label*="View query" i],
        [title*="Share" i],
        [title*="Save as" i],
        [title*="Edit chart" i],
        [title*="View query" i],
        [data-test*="share" i],
        [data-testid*="share" i],
        [data-test*="save-as" i],
        [data-testid*="save-as" i],
        [data-test*="edit-chart" i],
        [data-testid*="edit-chart" i],
        [data-test*="view-query" i],
        [data-testid*="view-query" i] {
          display: none !important;
        }
      `
    doc.head?.appendChild(style)
  }

  const alwaysBlockedLabels = new Set([
    'share',
    'share chart',
    'share dashboard',
    'save as',
    'edit chart',
    'view query',
  ])
  const actionNodes = doc.querySelectorAll<HTMLElement>('button, a, li, [role="button"], [role="menuitem"]')
  actionNodes.forEach((node) => {
    const text = (node.textContent ?? '').trim().toLowerCase()
    const aria = (node.getAttribute('aria-label') ?? '').trim().toLowerCase()
    const title = (node.getAttribute('title') ?? '').trim().toLowerCase()
    const testId = `${node.dataset.test ?? ''} ${node.dataset.testid ?? ''}`.toLowerCase()
    const isAlwaysBlocked =
      alwaysBlockedLabels.has(text) ||
      alwaysBlockedLabels.has(aria) ||
      alwaysBlockedLabels.has(title) ||
      testId.includes('share') ||
      testId.includes('save-as') ||
      testId.includes('edit-chart') ||
      testId.includes('view-query')
    if (isAlwaysBlocked) {
      node.style.display = 'none'
    }
  })
}
