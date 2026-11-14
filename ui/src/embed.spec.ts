import { describe, expect, test } from 'vitest'
import {
  altText,
  buildSnippet,
  embedFormats,
  type EmbedSource,
  idPrefix,
  imgTag,
  inlineSvg,
  linkTarget,
  pngSize,
  prefixSvgIds,
  toBase64,
} from './embed'

/** A PNG signature and IHDR declaring 300×340, which is all `pngSize` reads. */
function fakePng(width = 300, height = 340, padding = 0): Uint8Array {
  const bytes = new Uint8Array(33 + padding)
  bytes.set([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 13, 0x49, 0x48, 0x44, 0x52])
  const view = new DataView(bytes.buffer)
  view.setUint32(16, width)
  view.setUint32(20, height)
  return bytes
}

const SVG = `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="300" height="300" viewBox="0 0 300 300" role="img">
  <title>QR code</title>
  <defs>
    <linearGradient id="qr-gradient" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#000"/></linearGradient>
    <clipPath id="finder-outer"><rect x="0" y="0" width="70" height="70"/></clipPath>
    <mask id="finder-gap"><rect width="70" height="70" fill="#fff"/></mask>
  </defs>
  <mask id="finder-gap-0"><rect width="300" height="300" fill="#fff"/></mask>
  <g mask="url(#finder-gap-0)"><path d="M0 0h10v10z" fill="url(#qr-gradient)"/></g>
  <g mask="url(#finder-gap)"><image clip-path="url(#finder-outer)" href="data:image/png;base64,iVBORw0KGgo="/></g>
  <use href="#finder-outer"/>
  <use xlink:href="#qr-gradient"/>
  <rect fill="url(#elsewhere)"/>
</svg>
`

function source(overrides: Partial<EmbedSource> = {}): EmbedSource {
  return {
    render: async (format) =>
      format === 'svg'
        ? new Blob([SVG], { type: 'image/svg+xml' })
        : new Blob([fakePng() as BlobPart], { type: 'image/png' }),
    alt: 'QR code linking to example.com',
    href: null,
    ...overrides,
  }
}

describe('altText', () => {
  test('never includes a WiFi password', () => {
    const alt = altText('wifi', { ssid: 'Home', password: 's3cret-pass', encryption: 'WPA' }, 'WIFI:T:WPA;S:Home;P:s3cret-pass;;')
    expect(alt).toBe('QR code to join the WiFi network Home')
    expect(alt).not.toContain('s3cret')
  })

  test('names only the host of a URL, never its query', () => {
    const alt = altText('url', { url: 'example.com/reset?token=abc123' }, 'https://example.com/reset?token=abc123')
    expect(alt).toBe('QR code linking to example.com')
  })

  test('describes a short link by its host', () => {
    expect(altText('shortlink', {}, 'http://localhost:8080/r/abc123XYZ')).toBe(
      'QR code linking to localhost:8080',
    )
  })

  test('leaves out message bodies, IBANs and amounts', () => {
    expect(altText('sms', { number: '+1 555 0100', message: 'private' }, 'SMSTO:+15550100:private')).toBe(
      'QR code to text +1 555 0100',
    )
    expect(altText('email', { to: 'hi@example.com', body: 'private' }, 'mailto:hi@example.com?body=private')).toBe(
      'QR code to email hi@example.com',
    )
    const epc = altText('epc', { name: 'Red Cross', iban: 'BE72000000001616', amount: '12.30' }, 'BCD…')
    expect(epc).toBe('QR code for a SEPA payment to Red Cross')
    expect(altText('bitcoin', { address: 'bc1qxyz', amount: '1' }, 'bitcoin:bc1qxyz')).toBe(
      'QR code for a Bitcoin payment',
    )
  })

  test('keeps plain text private unless it is a URL', () => {
    expect(altText('text', { text: 'door code 1234' }, 'door code 1234')).toBe('QR code with text')
    expect(altText('text', { text: 'https://example.org/x' }, 'https://example.org/x')).toBe(
      'QR code linking to example.org',
    )
  })

  test('names contacts and events, shortened', () => {
    expect(altText('vcard', { firstName: 'Ada', lastName: 'Lovelace', phone: '+1' }, 'BEGIN:VCARD')).toBe(
      'QR code with contact details for Ada Lovelace',
    )
    const long = 'x'.repeat(200)
    expect(altText('event', { summary: long }, 'BEGIN:VEVENT').length).toBeLessThan(90)
  })
})

describe('linkTarget', () => {
  test('accepts http and https only', () => {
    expect(linkTarget('https://example.com/a')).toBe('https://example.com/a')
    expect(linkTarget('http://localhost:8080/r/abc')).toBe('http://localhost:8080/r/abc')
    expect(linkTarget('mailto:hi@example.com')).toBeNull()
    expect(linkTarget('javascript:alert(1)')).toBeNull()
    expect(linkTarget('WIFI:T:WPA;S:x;;')).toBeNull()
  })
})

describe('png helpers', () => {
  test('reads the size from the IHDR chunk', () => {
    expect(pngSize(fakePng(512, 600))).toEqual({ width: 512, height: 600 })
    expect(pngSize(new Uint8Array([1, 2, 3]))).toBeNull()
  })

  test('base64 matches btoa, across chunk boundaries', () => {
    const bytes = new Uint8Array(70_000).map((_, i) => i % 256)
    expect(toBase64(bytes)).toBe(btoa(String.fromCharCode(...bytes.slice(0, 60_000)) + String.fromCharCode(...bytes.slice(60_000))))
  })

  test('the img tag escapes its alt and carries the size', () => {
    expect(imgTag('data:image/png;base64,AA==', 'Say "hi" <now>', { width: 10, height: 12 })).toBe(
      '<img src="data:image/png;base64,AA==" alt="Say &quot;hi&quot; &lt;now&gt;" width="10" height="12">',
    )
  })
})

describe('inline SVG', () => {
  test('drops the XML declaration and uses the alt text as the title', () => {
    const svg = inlineSvg(SVG, 'QR code linking to example.com', 'p-')
    expect(svg.startsWith('<svg ')).toBe(true)
    expect(svg).not.toContain('<?xml')
    expect(svg).toContain('<title>QR code linking to example.com</title>')
  })

  test('prefixes every id and every reference to one', () => {
    const svg = prefixSvgIds(SVG, 'p-')
    const ids = [...svg.matchAll(/\sid="([^"]+)"/g)].map((match) => match[1])
    expect(ids).toEqual(['p-qr-gradient', 'p-finder-outer', 'p-finder-gap', 'p-finder-gap-0'])
    expect(svg).toContain('mask="url(#p-finder-gap-0)"')
    expect(svg).toContain('mask="url(#p-finder-gap)"')
    expect(svg).toContain('fill="url(#p-qr-gradient)"')
    expect(svg).toContain('clip-path="url(#p-finder-outer)"')
    expect(svg).toContain('<use href="#p-finder-outer"/>')
    expect(svg).toContain('<use xlink:href="#p-qr-gradient"/>')
    // Every local reference now points at a defined id.
    for (const [, id] of svg.matchAll(/url\(#([^)]+)\)|href="#([^"]+)"/g)) {
      if (id && id !== 'elsewhere') {
        expect(ids).toContain(id)
      }
    }
    // An undefined id and an embedded data URL are left alone.
    expect(svg).toContain('fill="url(#elsewhere)"')
    expect(svg).toContain('href="data:image/png;base64,iVBORw0KGgo="')
  })

  test('two prefixed copies share no id', () => {
    const a = prefixSvgIds(SVG, idPrefix(() => 0.1))
    const b = prefixSvgIds(SVG, idPrefix(() => 0.2))
    const idsOf = (svg: string) => new Set([...svg.matchAll(/\sid="([^"]+)"/g)].map((m) => m[1]))
    const shared = [...idsOf(a)].filter((id) => idsOf(b).has(id))
    expect(shared).toEqual([])
  })

  test('a document without ids is unchanged', () => {
    const plain = '<svg xmlns="http://www.w3.org/2000/svg"><rect fill="#000"/></svg>'
    expect(prefixSvgIds(plain, 'p-')).toBe(plain)
  })
})

describe('buildSnippet', () => {
  test('img is a PNG data URL with alt, width and height', async () => {
    const snippet = await buildSnippet('img', source())
    expect(snippet).toMatch(
      /^<img src="data:image\/png;base64,[A-Za-z0-9+/=]+" alt="QR code linking to example\.com" width="300" height="340">$/,
    )
  })

  test('dataurl is the data URL alone', async () => {
    expect(await buildSnippet('dataurl', source())).toBe(`data:image/png;base64,${toBase64(fakePng())}`)
  })

  test('svg is inline markup with prefixed ids', async () => {
    const snippet = await buildSnippet('svg', source(), 'qrabc-')
    expect(snippet.startsWith('<svg ')).toBe(true)
    expect(snippet).toContain('id="qrabc-qr-gradient"')
    expect(snippet).toContain('url(#qrabc-qr-gradient)')
  })

  test('link wraps the img in an anchor to the URL', async () => {
    const snippet = await buildSnippet('link', source({ href: 'http://localhost:8080/r/abc?x=1&y=2' }))
    expect(snippet).toMatch(/^<a href="http:\/\/localhost:8080\/r\/abc\?x=1&amp;y=2"><img src="data:image\/png;base64,[^"]+" alt="[^"]+" width="300" height="340"><\/a>$/)
  })

  test('requests the format the snippet needs', async () => {
    const formats: string[] = []
    const tracking = source()
    const render = tracking.render
    tracking.render = (format) => {
      formats.push(format)
      return render(format)
    }
    await buildSnippet('img', tracking)
    await buildSnippet('svg', tracking)
    expect(formats).toEqual(['png', 'svg'])
  })
})

describe('embedFormats', () => {
  test('offers the linked image only with a link target, and no Markdown', () => {
    expect(embedFormats(null).map((f) => f.value)).toEqual(['img', 'svg', 'dataurl'])
    expect(embedFormats('https://example.com').map((f) => f.value)).toEqual(['img', 'svg', 'dataurl', 'link'])
  })
})
