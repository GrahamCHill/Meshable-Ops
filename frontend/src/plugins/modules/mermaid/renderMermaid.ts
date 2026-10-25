import DOMPurify from 'dompurify'
import {diagramSourceError} from './mermaidSource'

let queue: Promise<unknown> = Promise.resolve()
let nextId = 0

/** Mermaid measures text in the host DOM; serialize all previews and export renders. */
export function renderMermaidSvg(source: string): Promise<string> {
  const render = queue.catch(() => undefined).then(async () => {
    const invalid = diagramSourceError(source)
    if (invalid) throw new Error(invalid)
    const mermaid = (await import('mermaid')).default
    mermaid.initialize({startOnLoad:false,securityLevel:'strict',theme:'default',htmlLabels:false,maxTextSize:20000,maxEdges:200,suppressErrorRendering:true,flowchart:{htmlLabels:false},secure:['securityLevel','startOnLoad','maxTextSize','maxEdges','suppressErrorRendering','htmlLabels']})
    const result = await mermaid.render(`knowledge-mermaid-${++nextId}`, source)
    return DOMPurify.sanitize(result.svg, {USE_PROFILES:{svg:true,svgFilters:true},FORBID_TAGS:['script','foreignObject','iframe','image'],FORBID_ATTR:['href','xlink:href','src']})
  })
  queue = render
  return render
}

export async function mermaidPng(source: string): Promise<string> {
  const svg = await renderMermaidSvg(source)
  const element = new DOMParser().parseFromString(svg, 'image/svg+xml').documentElement
  const bounds = (element.getAttribute('viewBox') ?? '').split(/[ ,]+/).map(Number)
  const width = bounds.length === 4 ? bounds[2] : parseFloat(element.getAttribute('width') ?? '800')
  const height = bounds.length === 4 ? bounds[3] : parseFloat(element.getAttribute('height') ?? '600')
  if (!Number.isFinite(width) || !Number.isFinite(height) || width <= 0 || height <= 0) throw new Error('This Mermaid diagram has invalid dimensions.')
  element.setAttribute('width', String(width)); element.setAttribute('height', String(height))
  const url = URL.createObjectURL(new Blob([new XMLSerializer().serializeToString(element)], {type:'image/svg+xml'}))
  try {
    const image = await new Promise<HTMLImageElement>((resolve, reject) => {
      const img = new Image(), timer = window.setTimeout(() => reject(new Error('Diagram rasterization timed out.')), 15000)
      img.onload = () => { clearTimeout(timer); resolve(img) }
      img.onerror = () => { clearTimeout(timer); reject(new Error('This Mermaid diagram could not be exported.')) }
      img.src = url
    })
    let scale = Math.min(2, 1800 / width, 2200 / height)
    for (let attempt = 0; attempt < 5; attempt++, scale *= .75) {
      const canvas = document.createElement('canvas')
      canvas.width = Math.max(1, Math.ceil(width * scale)); canvas.height = Math.max(1, Math.ceil(height * scale))
      const context = canvas.getContext('2d')
      if (!context) throw new Error('Your browser does not support image export.')
      context.fillStyle = '#ffffff'; context.fillRect(0, 0, canvas.width, canvas.height)
      context.drawImage(image, 0, 0, canvas.width, canvas.height)
      const png = canvas.toDataURL('image/png')
      if (png.length <= 350000) return png
    }
    throw new Error('This Mermaid diagram is too large. Split it into smaller diagrams.')
  } finally { URL.revokeObjectURL(url) }
}
