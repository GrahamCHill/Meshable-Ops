import { useEffect, useId, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { adminSettingsApi, type AdminSettings, type ReindexScheduleInput } from '../../../api/settings'
import { itemsApi } from '../../../api/items'
import { formatDate, getErrorMessage } from '../adminUtils'
import { useCredentialRequest } from '../useCredentialRequest'

export type SettingsForm = Pick<AdminSettings, 'storageProvider' | 'storageRootPath'> & ReindexScheduleInput & {
  storageBucket: string; storageRegion: string; storageEndpoint: string; storageAccessKey: string; storageSecretKey: string
}
const emptyForm: SettingsForm = {
  storageProvider: 'NONE', storageBucket: '', storageRegion: '', storageEndpoint: '', storageAccessKey: '',
  storageSecretKey: '', storageRootPath: 'folders', vectorReindexEnabled: false, vectorReindexIntervalMinutes: 1440,
  vectorReindexSchedule: 'INTERVAL', vectorReindexTime: '00:00', vectorReindexDayOfWeek: 'FRIDAY', vectorReindexTimeZone: 'Africa/Johannesburg',
}
function toForm(settings: AdminSettings): SettingsForm {
  return {
    storageProvider: settings.storageProvider,
    storageBucket: settings.storageBucket ?? '', storageRegion: settings.storageRegion ?? '',
    storageEndpoint: settings.storageEndpoint ?? '', storageAccessKey: '',
    storageSecretKey: '', storageRootPath: settings.storageRootPath,
    vectorReindexEnabled: settings.vectorReindexEnabled,
    vectorReindexIntervalMinutes: settings.vectorReindexIntervalMinutes,
    vectorReindexSchedule: settings.vectorReindexSchedule,
    vectorReindexTime: settings.vectorReindexTime,
    vectorReindexDayOfWeek: settings.vectorReindexDayOfWeek,
    vectorReindexTimeZone: settings.vectorReindexTimeZone,
  }
}

export type StorageSettingsTab = 'shared' | 'connection' | 'vectors'
export type StorageSettingsState = ReturnType<typeof useStorageSettings>

export function useStorageSettings() {
  const fieldId = useId()
  const queryClient = useQueryClient()
  const edited = useRef(false)
  const [form, setForm] = useState(emptyForm)
  const [identity, setIdentity] = useState(false)
  const [showSecret, setShowSecret] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const settings = useQuery({ queryKey: ['admin-settings'], queryFn: adminSettingsApi.get })
  const restore = (saved: AdminSettings) => {
    edited.current = false
    setForm(toForm(saved))
    setIdentity(!saved.storageAccessKeyConfigured && !saved.storageSecretKeyConfigured)
    setShowSecret(false)
  }
  useEffect(() => { if (settings.data && !edited.current) restore(settings.data) }, [settings.data])

  const check = useCredentialRequest({ mutationFn: (draft: SettingsForm) => adminSettingsApi.testStorage({ ...draft, useServerIdentity: identity }) })
  const save = useCredentialRequest({
    mutationFn: (draft: SettingsForm) => adminSettingsApi.update({ ...draft, useServerIdentity: identity }),
    onSuccess: saved => {
      restore(saved)
      check.reset()
      queryClient.setQueryData(['admin-settings'], saved)
      queryClient.invalidateQueries({ queryKey: ['items'] })
      setError('')
      setNotice(`Settings saved ${formatDate(saved.updatedAt)}`)
    },
    onError: err => { setNotice(''); setError(getErrorMessage(err, 'Unable to save settings.')) },
  })
  const reindex = useMutation({
    mutationFn: itemsApi.reindex,
    onSuccess: result => { queryClient.invalidateQueries({ queryKey: ['admin-settings'] }); setNotice(result.message); setError('') },
    onError: err => { setNotice(''); setError(getErrorMessage(err, 'Unable to recalculate vectors.')) },
  })
  const update = (patch: Partial<SettingsForm>) => {
    edited.current = true
    setForm(previous => ({ ...previous, ...patch }))
    setError(''); setNotice(''); check.reset()
  }
  const dirty = !!settings.data && (JSON.stringify(form) !== JSON.stringify(toForm(settings.data)) || identity !== (!settings.data.storageAccessKeyConfigured && !settings.data.storageSecretKeyConfigured))
  const azure = form.storageProvider === 'AZURE_BLOB'
  const enabled = form.storageProvider !== 'NONE'
  const busy = save.isPending || check.isPending
  const prefix = form.storageRootPath.trim().replace(/\\/g, '/').split('/').filter(Boolean).join('/')
  const sameLocation = !!settings.data && form.storageProvider === settings.data.storageProvider && form.storageBucket.trim() === (settings.data.storageBucket ?? '') && form.storageEndpoint.trim() === (settings.data.storageEndpoint ?? '')
  const validate = () => {
    const message = !prefix || prefix.split('/').some(part => part === '.' || part === '..') ? 'Enter a folder prefix without . or .. segments.'
      : enabled && !form.storageBucket.trim() ? `Enter a ${azure ? 'container' : 'bucket'} name.`
        : azure && !form.storageEndpoint.trim() ? 'Enter the Azure Blob service endpoint.'
          : enabled && !identity && ((!form.storageAccessKey.trim() && !(sameLocation && settings.data?.storageAccessKeyConfigured)) || (!form.storageSecretKey.trim() && !(sameLocation && settings.data?.storageSecretKeyConfigured))) ? 'Complete both credential fields or select server identity.'
            : enabled && !identity && !!form.storageAccessKey.trim() && !form.storageSecretKey.trim() ? 'Enter a new secret when replacing the account or access key.'
              : enabled && !identity && !settings.data?.encryptionReady ? 'Set APP_SECRETS_ENCRYPTION_KEY on the server before saving credentials.'
                : form.vectorReindexIntervalMinutes < 15 || form.vectorReindexIntervalMinutes > 10080 ? 'Choose an interval between 15 and 10080 minutes.' : ''
    setError(message)
    return !message
  }
  return { fieldId, form, identity, setIdentity, showSecret, setShowSecret, error, setError, notice, setNotice, settings, restore, check, save, reindex, update, dirty, azure, enabled, busy, prefix, validate }
}
