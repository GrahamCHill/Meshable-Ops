import { useQueryClient } from '@tanstack/react-query'

export function useRefreshStorage() {
  const client = useQueryClient()
  return () => {
    for (const key of ['storage-connections', 'admin-storage-connections', 'admin-storage-folders', 'items', 'storage-contents']) client.invalidateQueries({ queryKey: [key] })
  }
}
