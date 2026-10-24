import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { loginImageUrl } from '../../api/branding'
import { useBranding } from './Branding'
import '../../styles/branding.css'

export function LoginBackground({ children }: { children: ReactNode }) {
  const { data } = useBranding()
  const [active, setActive] = useState(0)
  const [paused, setPaused] = useState(false)
  const [failed, setFailed] = useState<Set<string>>(() => new Set())
  const [reducedMotion, setReducedMotion] = useState(false)
  const [visible, setVisible] = useState(() => !document.hidden)
  const settings = data?.settings
  const images = useMemo(() => {
    const available = (data?.loginImages ?? []).filter(image => !failed.has(loginImageUrl(image)))
    if (!settings || settings.loginBackgroundMode === 'NONE') return []
    if (settings.loginBackgroundMode === 'SINGLE') return [available.find(image => image.slot === settings.loginBackgroundSlot) ?? available[0]].filter(image => !!image)
    return available
  }, [data?.loginImages, settings, failed])
  const imageKey = images.map(image => `${image.slot}:${image.version}`).join(',')
  useEffect(() => { setActive(0) }, [imageKey])
  useEffect(() => {
    const preference = window.matchMedia('(prefers-reduced-motion: reduce)')
    const motion = () => setReducedMotion(preference.matches)
    const visibility = () => setVisible(!document.hidden)
    motion(); preference.addEventListener('change', motion); document.addEventListener('visibilitychange', visibility)
    return () => { preference.removeEventListener('change', motion); document.removeEventListener('visibilitychange', visibility) }
  }, [])
  useEffect(() => {
    if (images.length < 2 || paused || reducedMotion || !visible) return
    const timer = window.setInterval(() => setActive(index => (index + 1) % images.length), Math.max(5, Math.min(60, settings?.slideshowSeconds ?? 8)) * 1000)
    return () => window.clearInterval(timer)
  }, [imageKey, images.length, paused, reducedMotion, visible, settings?.slideshowSeconds])
  return <div className={`auth-screen${images.length ? ' has-background' : ''}`}>
    {!!images.length && <div className="auth-background" aria-hidden="true">{images.map((image, index) => <img key={loginImageUrl(image)} src={loginImageUrl(image)} alt="" className={index === active % images.length ? 'is-active' : ''} onError={() => setFailed(current => new Set(current).add(loginImageUrl(image)))} />)}</div>}
    <div className="auth-content">{children}
      {images.length > 1 && <div className="auth-background-controls" aria-label="Sign-in background slideshow">
        <button type="button" onClick={() => setActive(index => (index - 1 + images.length) % images.length)}>Previous image</button>
        <span>{active % images.length + 1} / {images.length}</span>
        <button type="button" onClick={() => setActive(index => (index + 1) % images.length)}>Next image</button>
        {reducedMotion ? <span>Reduced motion</span> : <button type="button" aria-pressed={paused} onClick={() => setPaused(value => !value)}>{paused ? 'Play slideshow' : 'Pause slideshow'}</button>}
      </div>}
    </div>
  </div>
}
