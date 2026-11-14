import { describe, expect, test } from 'vitest'
import { defaultStyle } from './qr/style'
import {
  decodeShare,
  encodeShare,
  MAX_SHARE_URL_LENGTH,
  parseFragment,
  shareableContent,
  shareableStyle,
  styleImages,
} from './share'

const image = `data:image/png;base64,${'A'.repeat(5000)}`

function base64url(text: string): string {
  return btoa(text).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

describe('share links', () => {
  test('round-trip a style, compressed', async () => {
    const style = defaultStyle()
    style.moduleShape = 'STAR'
    style.gradient = { type: 'RADIAL', start: '#000000', end: '#123456', angle: 0, finders: true }
    const token = await encodeShare({ style: shareableStyle(style) })
    expect(token).toMatch(/^v1\.z\.[A-Za-z0-9_-]+$/)
    expect(token.length).toBeLessThan(600)

    const decoded = await decodeShare(token)
    expect(decoded.style.moduleShape).toBe('STAR')
    expect(decoded.style.gradient).toEqual(style.gradient)
    expect(decoded.content).toBeUndefined()
  })

  test('round-trip without CompressionStream as plain JSON', async () => {
    const token = await encodeShare({ style: { size: 512 } }, false)
    expect(token.startsWith('v1.j.')).toBe(true)
    expect((await decodeShare(token)).style).toEqual({ size: 512 })
  })

  test('round-trip content, with secrets left out', async () => {
    const { content, omitted } = shareableContent('wifi', {
      ssid: 'Home',
      encryption: 'WPA',
      password: 'hunter2',
      hidden: '',
    })
    expect(content).toEqual({ type: 'wifi', values: { ssid: 'Home', encryption: 'WPA' } })
    expect(omitted).toEqual(['Password'])

    const token = await encodeShare({ style: {}, content })
    expect((await decodeShare(token)).content).toEqual(content)
  })

  test('a crafted link cannot fill a secret field', async () => {
    const token = await encodeShare({
      style: {},
      content: { type: 'wifi', values: { ssid: 'Evil', password: 'planted' } },
    })
    expect((await decodeShare(token)).content?.values).toEqual({ ssid: 'Evil' })
  })

  test('leave images out of the style, and name them', async () => {
    const style = defaultStyle()
    style.logo = { dataUrl: image, sizePercent: 20, paddingPercent: 8, background: '#fff', radiusPercent: 25 }
    style.backgroundImage = { dataUrl: image }
    style.finderArtwork = { dataUrl: image }
    // Artwork only counts with the custom corner shape.
    expect(styleImages(style)).toEqual(['logo', 'background image'])
    style.finderShape = 'CUSTOM'
    expect(styleImages(style)).toEqual(['logo', 'background image', 'corner artwork'])

    const shared = shareableStyle(style)
    expect(shared).not.toHaveProperty('logo')
    expect(shared).not.toHaveProperty('backgroundImage')
    expect(shared).not.toHaveProperty('finderArtwork')

    // A link that carries one anyway has it ignored.
    const token = await encodeShare({ style: { ...shared, logo: style.logo } })
    expect((await decodeShare(token)).style).not.toHaveProperty('logo')
  })

  test('reject other versions, damage and bad shapes with a message', async () => {
    await expect(decodeShare('v2.z.AAAA')).rejects.toThrow('newer version')
    await expect(decodeShare('nonsense')).rejects.toThrow('damaged')
    await expect(decodeShare('v1.z.!!!!')).rejects.toThrow('damaged')
    await expect(decodeShare('v1.z.AAAAAAAA')).rejects.toThrow('damaged')
    await expect(decodeShare('v1.q.AAAA')).rejects.toThrow('damaged')
    await expect(decodeShare(`v1.j.${base64url('not json')}`)).rejects.toThrow('damaged')
    await expect(decodeShare(`v1.j.${base64url('{"style":{"size":"big"}}')}`)).rejects.toThrow(
      'valid design',
    )
    await expect(decodeShare(`v1.j.${base64url('{"style":{"frame":{"title":{}}}}')}`)).rejects.toThrow(
      'valid design',
    )
    await expect(
      decodeShare(`v1.j.${base64url('{"style":{},"content":{"type":"nope","values":{}}}')}`),
    ).rejects.toThrow('valid design')
  })

  test('refuse a link that inflates past the limit', async () => {
    const token = await encodeShare({ style: { foreground: 'x'.repeat(200_000) } })
    expect(token.length).toBeLessThan(MAX_SHARE_URL_LENGTH)
    await expect(decodeShare(token)).rejects.toThrow('more than a design')
  })
})

describe('parseFragment', () => {
  test('reads share and design fragments', () => {
    expect(parseFragment('#share=v1.z.abc')).toEqual({ kind: 'share', token: 'v1.z.abc' })
    expect(parseFragment('#design=42')).toEqual({ kind: 'design', id: 42 })
  })

  test('ignores fragments that are not ours', () => {
    expect(parseFragment('')).toBeNull()
    expect(parseFragment('#top')).toBeNull()
  })

  test('flags bad ids and oversized links', () => {
    expect(parseFragment('#design=abc')).toMatchObject({ kind: 'invalid' })
    expect(parseFragment('#design=0')).toMatchObject({ kind: 'invalid' })
    expect(parseFragment(`#share=v1.z.${'A'.repeat(MAX_SHARE_URL_LENGTH)}`)).toMatchObject({
      kind: 'invalid',
      message: expect.stringContaining('too long'),
    })
  })
})
