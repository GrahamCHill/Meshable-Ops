// Append to existing client.ts — add these exports

export const supersetApi = {
  /**
   * Ask the Java backend to mint a Superset guest token.
   * The backend authenticates as admin service-to-service; the browser
   * never sees admin credentials.
   */
  guestToken: (dashboardIds: string[]): Promise<string> =>
    import('./client').then(({ default: api }) =>
      api.post<{ token: string }>('/superset/guest-token', { dashboardIds })
        .then(r => r.data.token)
    ),
}
