export function storageLink(folderId: string, filePath = '') {
  const path = filePath.includes('/') ? filePath.slice(0, filePath.lastIndexOf('/') + 1) : ''
  const params = new URLSearchParams({ folder: folderId })
  if (path) params.set('path', path)
  return `/storage?${params.toString()}`
}
