import { clone, defaultTemplate, newSection, type DocumentTemplate } from '../../vendor/weasycanvas/src/templates'
import type { TemplateInput } from '../../api/documents'

export function blankTemplate(): TemplateInput {
  const source: DocumentTemplate = {
    ...clone(defaultTemplate),
    id: crypto.randomUUID(),
    name: 'New document template',
    description: '',
    sections: [newSection('title'), { ...newSection('text'), id: 'content' }],
    rules: { minParagraphs: 1, maxParagraphs: 100, allowSubparagraphs: false, allowSubsubparagraphs: false },
    apiFields: [],
    defaults: {
      organization: '', title: '', reference: '', recipient: '', closing_text: '',
      paragraphs: [''], paragraphSectionIds: ['content'], subparagraphs: [[]], subsubparagraphs: [[]], numbered: false,
    },
  }
  return { name: source.name, category: '', published: false, revision: 0, template: source, fieldRules: [] }
}
