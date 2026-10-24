import { useQuery } from '@tanstack/react-query'
import api from './http'
import { useAuthStore } from '../hooks/useAuthStore'
import {bundledRoutes} from '../plugins/registry'
import { adminGroups } from '../components/admin/adminNavigation'

export type AccountRole = 'SUPER_ADMIN' | 'ADMIN' | 'BUSINESS_MANAGER' | 'USER' | 'EXTERNAL'
export const isAdminRole = (role?: string) => role === 'SUPER_ADMIN' || role === 'ADMIN'
export const pluginRole = (role: string) => role === 'SUPER_ADMIN' ? 'ADMIN' : role === 'BUSINESS_MANAGER' ? 'USER' : role
export interface AccessFeature { id: string; label: string; path: string; admin: boolean }
export interface StorageRule { folderId: string; path: string; type: 'FOLDER' | 'FILE' }
export interface MemberAccess { features: string[] | null; restrictStorage: boolean; grants: StorageRule[]; blocks: StorageRule[] }
export interface AccessPolicy { roleFeatures: Record<string, string[]>; members: Record<string, MemberAccess>; protectedPaths: {rule: StorageRule; externalOnly: boolean}[] }
export interface AccessPolicyView { revision: number; policy: AccessPolicy; features: AccessFeature[] }
export interface AccessRoot { id: string; title: string; connection?: string; accessible: boolean }
interface MyAccess { features: string[]; role: string; views: AccessFeature[] }
export const accessApi = {
  me: () => api.get<MyAccess>('/access/me').then(r => r.data),
  get: () => api.get<AccessPolicyView>('/admin/access-controls').then(r => r.data),
  save: (value: Pick<AccessPolicyView, 'revision' | 'policy'>) => api.put<AccessPolicyView>('/admin/access-controls', value).then(r => r.data),
  roots: () => api.get<AccessRoot[]>('/admin/access-controls/storage-roots').then(r => r.data),
}
export function useAccess() {
  const { user, token } = useAuthStore()
  const query = useQuery({ queryKey: ['my-access', user?.id, token], queryFn: accessApi.me, enabled: !!token, refetchInterval: 15_000, refetchOnWindowFocus: 'always' })
  const can = (id: string) => query.data?.features.includes(id) === true
  const canPath = (path: string): boolean => {
    path = path.split('?')[0]
    if (path.startsWith('/account/')) return true
    if (path === '/admin') return isAdminRole(query.data?.role) && query.data?.views.some(view => view.admin && can(view.id)) === true
    if (path.startsWith('/admin/sections/')) return isAdminRole(query.data?.role) && (can('admin.plugins') || bundledRoutes.some(route => route.adminCategory && path === `/admin/sections/${route.adminCategory}` && can(route.feature)) || adminGroups.find(group => path === `/admin/sections/${group.id}`)?.items.some(item => canPath(item.to)) === true)
    if (path === '/admin/email') return can('admin.notifications')
    if (path === '/items') path = '/storage'
    if (path === '/events') path = '/admin/events'
    const view = query.data?.views.filter(view => path === view.path || view.path !== '/' && path.startsWith(view.path + '/')).sort((a, b) => b.path.length - a.path.length)[0]
    return !!view && can(view.id)
  }
  return { ...query, can, canPath }
}
