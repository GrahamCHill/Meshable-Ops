import { create } from 'zustand'
import { persist } from 'zustand/middleware'

// Remove credentials left by earlier releases; only a non-secret UI marker is persisted.
localStorage.removeItem('token')

interface AuthUser {
  id: string
  email: string
  username: string
  name?: string
  contactNumber?: string
  role: string
  canUploadDocuments: boolean
}

interface AuthState {
  token: string | null
  user: AuthUser | null
  login: (token: string, user: AuthUser) => void
  logout: () => void
  setUploadPermission: (canUploadDocuments: boolean) => void
  refreshUser: (user: AuthUser) => void
  isAuthenticated: () => boolean
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      token: null,
      user: null,
      login: (_token, user) => {
        localStorage.removeItem('token')
        set({ token: user.id+':cookie-session', user })
      },
      logout: () => {
        localStorage.removeItem('token')
        set({ token: null, user: null })
      },
      setUploadPermission: canUploadDocuments => set(state => ({
        user: state.user ? { ...state.user, canUploadDocuments } : null,
      })),
      refreshUser: user => set({ user }),
      isAuthenticated: () => !!get().token,
    }),
    { name: 'auth-storage', version: 2, migrate: () => ({token:null,user:null}), partialize: state => ({ token: state.user ? state.user.id+':cookie-session' : null, user: state.user }) }
  )
)
