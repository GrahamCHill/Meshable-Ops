import {createContext, useContext, useMemo, useState, type Dispatch, type ReactNode, type SetStateAction} from 'react'
import {useLocation} from 'react-router-dom'
import {useAuthStore} from './useAuthStore'

const Drafts = createContext<Map<string, unknown> | null>(null)

/** In-memory drafts last only for the current page and account. Nothing is written to browser storage. */
export function PageDraftProvider({children}: {children: ReactNode}) {
  const {pathname, search} = useLocation()
  const userId = useAuthStore(state => state.user?.id)
  const values = useMemo(() => new Map<string, unknown>(), [pathname, search, userId])
  return <Drafts.Provider value={values}>{children}</Drafts.Provider>
}

export function usePageDraft<T>(key: string, initial: T | (() => T)): [T, Dispatch<SetStateAction<T>>, () => void] {
  const shared = useContext(Drafts)
  const [fallback] = useState(() => new Map<string, unknown>())
  const values = shared ?? fallback
  const [, refresh] = useState(0)
  if (!values.has(key)) values.set(key, typeof initial === 'function' ? (initial as () => T)() : initial)
  const update: Dispatch<SetStateAction<T>> = next => {
    values.set(key, typeof next === 'function' ? (next as (value: T) => T)(values.get(key) as T) : next)
    refresh(value => value + 1)
  }
  return [values.get(key) as T, update, () => {values.delete(key); refresh(value => value + 1)}]
}

export function useDiscardPageDraft() {
  const values = useContext(Drafts)
  return (key: string) => values?.delete(key)
}
