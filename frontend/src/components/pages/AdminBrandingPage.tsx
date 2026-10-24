import { useBranding } from '../layout/Branding'
import { AdminPageHeader } from '../admin/AdminPageHeader'
import { BrandImageSetting } from '../admin/branding/BrandImageSetting'
import { BrandingAppearanceForm } from '../admin/branding/BrandingAppearanceForm'
import { BrandingBackgroundImages } from '../admin/branding/BrandingBackgroundImages'
import { Button } from '../ui'
import '../../styles/branding.css'

export function AdminBrandingPage() {
  const branding = useBranding()
  return <div className="page-stack branding-page">
    <AdminPageHeader title="Branding" description="Use your business name, colours, and images throughout the workspace and sign-in screen." />
    <p className="field-help">Branding is public, including before sign-in. Use images suitable for public display.</p>
    {branding.isError ? <p role="alert">Unable to load branding. <Button variant="ghost" onClick={() => void branding.refetch()}>Retry</Button></p> : !branding.data ? <p role="status">Loading branding…</p> : <>
      <BrandingAppearanceForm branding={branding.data} />
      <section className="page-stack"><h2>Logo and favicon</h2><p className="field-help">PNG, JPEG, or WebP up to 1 MiB and 2048 × 2048 pixels. Uploads are resized and saved as PNGs without their original metadata.</p><div className="branding-assets"><BrandImageSetting kind="LOGO" branding={branding.data} /><BrandImageSetting kind="FAVICON" branding={branding.data} /></div></section>
      <BrandingBackgroundImages images={branding.data.loginImages ?? []} />
    </>}
  </div>
}
