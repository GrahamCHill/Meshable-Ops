import { clone, defaultTemplate, newSection, type DocumentTemplate } from '../../vendor/weasycanvas/src/templates'
import type { TemplateInput, FieldRule } from '../../api/documents'

export function starterTemplate(category: TemplateInput['category']): TemplateInput {
  const source: DocumentTemplate = { ...clone(defaultTemplate), id: crypto.randomUUID(), name: category === 'OTHER' ? 'New document template' : category === 'QUOTE' ? 'Quote' : 'Invoice' }
  const fieldRules: FieldRule[] = []
  if (category !== 'OTHER') {
    const basic = [
      ['company.name', 'Organisation', 'TEXT', true], ['document.number', 'Document number', 'TEXT', true], ['document.date', 'Date', 'DATE', true],
      ['customer.name', 'Customer', 'TEXT', true], ['line_items', 'Items and descriptions', 'MULTILINE', true],
      ['subtotal', 'Subtotal', 'MONEY', true], ['tax_rate', 'Tax rate (%)', 'NUMBER', true], ['tax', 'Tax', 'DERIVED', false], ['total', 'Total', 'DERIVED', false],
    ] as const
    source.apiFields = basic.map(([key, label, , required]) => ({ key, label, required, maxLength: key === 'line_items' ? 3000 : key === 'document.number' ? 100 : 160, fallback: key === 'tax_rate' ? '0' : '' }))
    fieldRules.push(...basic.map(([key, , type]) => ({ key, type, inputs: type === 'DERIVED' ? key === 'tax' ? ['subtotal', 'tax_rate'] : ['subtotal', 'tax'] : [], ...(type === 'DERIVED' ? { operation: key === 'tax' ? 'PERCENT' as const : 'ADD' as const } : {}) })))
    source.description = 'Data-driven document with subtotal, tax and total. Edit the wording, fields and layout for your organisation.'
    source.sections = [newSection('header'), newSection('title'), { ...newSection('details'), bindings: { reference: 'document.number', recipient: 'customer.name' } },
      { ...newSection('text'), id: 'items', label: 'Items', maxLength: 4000, rules: { minParagraphs: 1, maxParagraphs: 10, allowSubparagraphs: false, allowSubsubparagraphs: false } }, newSection('closing'), newSection('footer')]
    source.rules = { minParagraphs: 1, maxParagraphs: 10, allowSubparagraphs: false, allowSubsubparagraphs: false }
    source.defaults = { organization: '{{api:company.name}}', title: source.name, reference: '', recipient: '',
      paragraphs: ['Date: {{api:document.date}}\n\n{{api:line_items}}\n\nSubtotal: {{api:subtotal}}\nTax ({{api:tax_rate}}%): {{api:tax}}\nTotal: {{api:total}}'],
      paragraphSectionIds: ['items'], subparagraphs: [[]], subsubparagraphs: [[]], closing_text: category === 'QUOTE' ? 'Please contact us to accept this quotation.' : 'Please include the invoice number with your payment.' }
  }
  return { name: source.name, category, published: false, revision: 0, template: source, fieldRules }
}
