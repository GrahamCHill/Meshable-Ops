export function diagramSourceError(source:string) {
  if(!source.trim()||source.length>20000)return 'Enter diagram source, up to 20,000 characters.'
  if(/%%\s*\{|^\s*---/m.test(source))return 'Use diagram syntax without runtime configuration directives or front matter.'
  // Mermaid measures labels in the host DOM before returning SVG, so reject resource-loading syntax before rendering.
  if(/\b(?:img|image)\s*:|<\s*(?:img|image|iframe|object|embed|link|script|style|meta|base)\b|url\s*\(|@(?:import|font-face)\b|!\[[^\]]*\]\s*\(/i.test(source))return 'Embedded images, HTML resources and external styles are not supported in local diagram previews.'
  return undefined
}
