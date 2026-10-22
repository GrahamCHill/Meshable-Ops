export function getErrorMessage(error: unknown, fallback = 'Unable to save right now.') {
  if (!error || typeof error !== 'object') return fallback

  const response = (error as {
    response?: { data?: { message?: string } | Record<string, string> }
  }).response

  if (!response?.data) return fallback
  if ('message' in response.data && typeof response.data.message === 'string') {
    return response.data.message
  }

  const firstFieldError = Object.values(response.data)[0]
  return typeof firstFieldError === 'string' ? firstFieldError : fallback
}

export function formatDate(value?: string | null) {
  return value ? new Date(value).toLocaleString() : 'Never'
}

export function extractFilename(contentDisposition?: string) {
  if (!contentDisposition) return 'app-backup.json'
  const match = /filename="?(.*?)"?$/i.exec(contentDisposition)
  return match?.[1] ?? 'app-backup.json'
}
