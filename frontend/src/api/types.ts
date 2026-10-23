

export type StorageProvider = 'NONE' | 'S3' | 'AZURE_BLOB'

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}
