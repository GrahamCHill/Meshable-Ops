import {useQuery} from '@tanstack/react-query'
import api from './http'
import {useAuthStore} from '../hooks/useAuthStore'
export interface Profile {id: string; name: string; username: string; email: string; contactNumber: string; role: string; timeZone: string; avatarVersion?: string; revision: number}
export const profileApi = {
  get: () => api.get<Profile>('/account/profile').then(r => r.data),
  save: (value: Profile) => api.put<Profile>('/account/profile', value).then(r => r.data),
  avatar: (file: File) => {const body = new FormData(); body.append('file', file); return api.post<Profile>('/account/profile/avatar', body, {headers: {'Content-Type': undefined}}).then(r => r.data)},
  removeAvatar: () => api.delete<Profile>('/account/profile/avatar').then(r => r.data),
}
export function useProfile() {const user = useAuthStore(state => state.user); return useQuery({queryKey: ['account-profile', user?.id],queryFn: profileApi.get,enabled: !!user})}
