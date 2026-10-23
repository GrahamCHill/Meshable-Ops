import api from './http'

export const supersetApi = {
  /**
   * Ask the Java backend to mint a Superset guest token.
   * The backend authenticates as admin service-to-service; the browser
   * never sees admin credentials.
   */
  guestToken: (dashboardIds: string[], appOrigin?: string): Promise<string> =>
    api.post<{ token: string }>('/superset/guest-token', { dashboardIds, appOrigin })
      .then(r => r.data.token),
}
