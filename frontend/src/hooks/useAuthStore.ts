import { create } from 'zustand'
import { persist } from 'zustand/middleware'

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
      login: (token, user) => {
        localStorage.setItem('token', token)
        set({ token, user })
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
    { name: 'auth-storage', partialize: state => ({ token: state.token, user: state.user }) }
  )
)
