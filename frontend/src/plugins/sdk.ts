import api from '../api/http'

/** Shared authentication and error handling, with routes scoped to this plugin. This is not a sandbox. */
export function createPluginApi(id: string) {
  if (!/^[a-z][a-z0-9-]{1,39}$/.test(id)) throw new Error('Invalid plugin ID')
  const path = (route: string) => {
    if (!/^\/[a-zA-Z0-9/_-]*$/.test(route) || route.includes('//')) throw new Error('Use a relative plugin API path')
    return `/plugins/${id}${route}`
  }
  return {
    get: <T>(route: string) => api.get<T>(path(route)).then(response => response.data),
    post: <T>(route: string, body: unknown) => api.post<T>(path(route), body).then(response => response.data),
    put: <T>(route: string, body: unknown) => api.put<T>(path(route), body).then(response => response.data),
  }
}
