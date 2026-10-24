import type { Edge } from '@xyflow/react'
import type { StepNode } from './WorkflowStep'

/** Basic layered layout for the initial acyclic block set; incomplete drafts also get a position. */
export function arrangeWorkflow(nodes: StepNode[], edges: Edge[]): StepNode[] {
  const counts = new Map(nodes.map(node => [node.id, edges.filter(edge => edge.target === node.id).length]))
  const levels = new Map<string, number>(); const queue = nodes.filter(node => counts.get(node.id) === 0).map(node => node.id)
  for (let index = 0; index < queue.length; index++) {
    const id = queue[index]; const level = levels.get(id) ?? 0; levels.set(id, level)
    for (const edge of edges.filter(edge => edge.source === id)) {
      levels.set(edge.target, Math.max(levels.get(edge.target) ?? 0, level + 1))
      counts.set(edge.target, (counts.get(edge.target) ?? 1) - 1)
      if (counts.get(edge.target) === 0) queue.push(edge.target)
    }
  }
  let last = Math.max(0,...levels.values())
  for (const node of nodes) if (!levels.has(node.id)) levels.set(node.id, ++last)
  const rows = new Map<number, number>()
  return nodes.map(node => { const level = levels.get(node.id)!; const column = rows.get(level) ?? 0; rows.set(level, column + 1); return { ...node, position: { x: 60 + column * 250, y: 50 + level * 150 } } })
}
