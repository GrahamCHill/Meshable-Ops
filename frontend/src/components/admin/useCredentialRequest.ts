import { useRef, useState } from 'react'
import { getErrorMessage } from './adminUtils'

/** Credential drafts and Axios request bodies must not remain in the shared mutation cache. */
export function useCredentialRequest<TInput, TResult>(options: {
  mutationFn: (input: TInput) => Promise<TResult>
  onSuccess?: (result: TResult) => void
  onError?: (error: unknown) => void
}) {
  const generation = useRef(0)
  const [state, setState] = useState<{ status: 'idle' | 'pending' | 'success' | 'error'; data?: TResult; error?: unknown }>({ status: 'idle' })
  const reset = () => { generation.current++; setState({ status: 'idle' }) }
  const mutate = (input: TInput) => {
    const request = ++generation.current
    setState({ status: 'pending' })
    void (async () => {
      try {
        const data = await options.mutationFn(input)
        if (request !== generation.current) return
        setState({ status: 'success', data })
        options.onSuccess?.(data)
      } catch (failure) {
        if (request !== generation.current) return
        // Axios errors include the original JSON body. Retain only the server's safe message.
        const error = { response: { data: { message: getErrorMessage(failure, 'The request failed. Check your details and try again.') } } }
        setState({ status: 'error', error })
        options.onError?.(error)
      }
    })()
  }
  return { ...state, isPending: state.status === 'pending', isSuccess: state.status === 'success', isError: state.status === 'error', mutate, reset }
}
