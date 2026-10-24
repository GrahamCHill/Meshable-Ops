import axios from 'axios'
import {useAuthStore} from '../hooks/useAuthStore'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? '/api',
  headers: { 'Content-Type': 'application/json' },
  withCredentials: true,
})

// Credentials and the CSRF header are sent only to the configured application API.
api.interceptors.request.use(config => {
  const base=new URL(api.defaults.baseURL??'/api',window.location.origin)
  const target=new URL(config.url??'',base.href.replace(/\/?$/,'/'))
  if(target.protocol!=='https:'&&!['localhost','127.0.0.1','[::1]'].includes(target.hostname))throw new Error('Use HTTPS for application API requests outside localhost')
  if(!['http:','https:'].includes(target.protocol)||target.origin!==base.origin)throw new Error('API requests must stay on the configured application origin')
  delete config.headers.Authorization
  const csrf=document.cookie.split('; ').find(item=>item.startsWith('__Host-XSRF-TOKEN='))??document.cookie.split('; ').find(item=>item.startsWith('XSRF-TOKEN='))
  if(csrf&& !['get','head','options'].includes((config.method??'get').toLowerCase()))config.headers['X-XSRF-TOKEN']=decodeURIComponent(csrf.slice(csrf.indexOf('=')+1))
  return config
})

// Auto-logout on 401
api.interceptors.response.use(
  res => res,
  err => {
    const authAttempt = /\/auth\/(login|mfa\/)|\/account\/mfa/.test(err.config?.url ?? '')
    if (err.response?.status === 401 && !authAttempt && err.response?.headers?.['x-meshable-plugin-response'] !== '1') {
      useAuthStore.getState().logout()
      window.location.href = '/login'
    }
    return Promise.reject(err)
  }
)

export default api
