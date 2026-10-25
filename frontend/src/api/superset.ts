import api from './http'

export interface SupersetAccount {
  username: string
  loginPath: string
  created: boolean
}

export const supersetApi = {
  setPassword: (password: string): Promise<SupersetAccount> =>
    api.post<SupersetAccount>('/superset/account/password', { password }).then(r => r.data),
  /**
   * Ask the Java backend to mint a Superset guest token.
   * The backend authenticates as admin service-to-service; the browser
   * never sees admin credentials.
   */
  guestToken: (dashboardIds: string[], appOrigin?: string): Promise<string> =>
    api.post<{ token: string }>('/superset/guest-token', { dashboardIds, appOrigin })
      .then(r => r.data.token),
}
