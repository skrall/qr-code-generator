import { describe, expect, test } from 'vitest'
import {
  escapeIcal,
  escapeVCard,
  escapeWifi,
  findQrType,
  icalDate,
  initialValues,
  QR_TYPES,
} from './types'

function build(id: Parameters<typeof findQrType>[0], values: Record<string, string>): string {
  return findQrType(id).build(values)
}

describe('URL', () => {
  test('adds a scheme when one is missing', () => {
    expect(build('url', { url: 'example.com/a?b=c' })).toBe('https://example.com/a?b=c')
  })

  test('keeps an explicit scheme', () => {
    expect(build('url', { url: 'mailto:hello@example.com' })).toBe('mailto:hello@example.com')
    expect(build('url', { url: 'http://example.com' })).toBe('http://example.com')
  })

  test('is empty without a URL', () => {
    expect(build('url', {})).toBe('')
  })
})

describe('email', () => {
  test('is a bare mailto without subject or body', () => {
    expect(build('email', { to: 'hello@example.com' })).toBe('mailto:hello@example.com')
  })

  test('encodes the subject and body', () => {
    expect(build('email', { to: 'hello@example.com', subject: 'A & B', body: 'line one\nline two' })).toBe(
      'mailto:hello@example.com?subject=A%20%26%20B&body=line%20one%0Aline%20two',
    )
  })
})

describe('phone and SMS', () => {
  test('phone strips formatting', () => {
    expect(build('phone', { number: '+1 (555) 010-0' })).toBe('tel:+15550100')
  })

  test('SMS keeps the number and flattens newlines', () => {
    expect(build('sms', { number: '+1 555 0100', message: 'hi\nthere' })).toBe('SMSTO:+15550100:hi there')
  })

  test('SMS without a message still names the number', () => {
    expect(build('sms', { number: '5550100' })).toBe('SMSTO:5550100:')
  })
})

describe('WiFi', () => {
  test('writes the standard grammar', () => {
    expect(build('wifi', { ssid: 'Cafe', password: 'espresso', encryption: 'WPA' })).toBe(
      'WIFI:T:WPA;S:Cafe;P:espresso;;',
    )
  })

  test('escapes the separators in an SSID', () => {
    expect(build('wifi', { ssid: 'a;b,c:d"e\\f', password: 'x', encryption: 'WPA' })).toBe(
      'WIFI:T:WPA;S:a\\;b\\,c\\:d\\"e\\\\f;P:x;;',
    )
  })

  test('omits the password for an open network', () => {
    expect(build('wifi', { ssid: 'Cafe', encryption: 'nopass' })).toBe('WIFI:T:nopass;S:Cafe;;')
  })

  test('marks a hidden network', () => {
    expect(build('wifi', { ssid: 'Cafe', password: 'x', encryption: 'WPA', hidden: 'true' })).toBe(
      'WIFI:T:WPA;S:Cafe;P:x;H:true;;',
    )
  })
})

describe('vCard', () => {
  test('uses CRLF and the vCard 3.0 layout', () => {
    const payload = build('vcard', {
      firstName: 'Ada',
      lastName: 'Lovelace',
      org: 'Analytical Engines',
      phone: '+15550100',
      email: 'ada@example.com',
    })

    expect(payload).toBe(
      [
        'BEGIN:VCARD',
        'VERSION:3.0',
        'N:Lovelace;Ada;;;',
        'FN:Ada Lovelace',
        'ORG:Analytical Engines',
        'TEL;TYPE=CELL:+15550100',
        'EMAIL;TYPE=INTERNET:ada@example.com',
        'END:VCARD',
      ].join('\r\n'),
    )
  })

  test('escapes separators and newlines', () => {
    expect(escapeVCard('a;b,c\\d\ne')).toBe('a\\;b\\,c\\\\d\\ne')
  })

  test('needs at least one name', () => {
    expect(build('vcard', { org: 'Analytical Engines' })).toBe('')
  })
})

describe('event', () => {
  test('formats a floating local start and end', () => {
    const payload = build('event', {
      summary: 'Offsite',
      start: '2026-11-11T09:00',
      end: '2026-11-11T17:30',
      location: 'The office',
    })

    expect(payload).toContain('DTSTART:20261111T090000')
    expect(payload).toContain('DTEND:20261111T173000')
    expect(payload).toContain('LOCATION:The office')
    expect(payload.startsWith('BEGIN:VCALENDAR')).toBe(true)
    expect(payload.endsWith('END:VCALENDAR')).toBe(true)
  })

  test('omits an absent end', () => {
    expect(build('event', { summary: 'Offsite', start: '2026-11-11T09:00' })).not.toContain('DTEND')
  })

  test('icalDate pads missing seconds', () => {
    expect(icalDate('2026-11-11T09:00')).toBe('20261111T090000')
    expect(icalDate('2026-11-11T09:00:30')).toBe('20261111T090030')
  })

  test('escapes iCalendar text', () => {
    expect(escapeIcal('a;b,c\nd')).toBe('a\\;b\\,c\\nd')
  })
})

describe('geo and bitcoin', () => {
  test('geo needs both coordinates', () => {
    expect(build('geo', { latitude: '51.5007', longitude: '-0.1246' })).toBe('geo:51.5007,-0.1246')
    expect(build('geo', { latitude: '51.5007' })).toBe('')
  })

  test('bitcoin encodes the optional parameters', () => {
    expect(build('bitcoin', { address: 'bc1qxyz', amount: '0.001', label: 'Coffee fund' })).toBe(
      'bitcoin:bc1qxyz?amount=0.001&label=Coffee%20fund',
    )
  })

  test('bitcoin without parameters is the bare URI', () => {
    expect(build('bitcoin', { address: 'bc1qxyz' })).toBe('bitcoin:bc1qxyz')
  })
})

describe('the catalogue', () => {
  test('every type has a unique id and a hint', () => {
    const ids = QR_TYPES.map((type) => type.id)
    expect(new Set(ids).size).toBe(ids.length)
    expect(QR_TYPES.every((type) => type.hint.length > 0)).toBe(true)
  })

  test('initialValues copies defaults rather than sharing them', () => {
    const wifi = findQrType('wifi')
    const first = initialValues(wifi)
    const second = initialValues(wifi)

    expect(first).toEqual({ encryption: 'WPA' })
    first.encryption = 'WEP'
    expect(second.encryption).toBe('WPA')
  })

  test('escapeWifi escapes every separator', () => {
    expect(escapeWifi('a\\b;c,d:e"f')).toBe('a\\\\b\\;c\\,d\\:e\\"f')
  })
})
