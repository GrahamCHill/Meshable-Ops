export function validColour(value: string | null | undefined): value is string { return !!value && /^#[0-9a-f]{6}$/i.test(value) }
export function sidebarForeground(background: string) {
  const luminance = [1, 3, 5].map(offset => Number.parseInt(background.slice(offset, offset + 2), 16) / 255)
    .map(value => value <= .04045 ? value / 12.92 : ((value + .055) / 1.055) ** 2.4)
    .reduce((total, value, index) => total + value * [.2126, .7152, .0722][index], 0)
  return (luminance + .05) / .05 >= 1.05 / (luminance + .05) ? '#000000' : '#ffffff'
}
