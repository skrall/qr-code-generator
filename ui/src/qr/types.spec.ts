import { describe, expect, test } from 'vitest'
import {
  epcAmount,
  escapeIcal,
  escapeMeCard,
  escapeVCard,
  escapeWifi,
  findQrType,
  icalDate,
  initialValues,
  isValidIban,
  mod97Valid,
  QR_TYPES,
} from './types'

type Id = Parameters<typeof findQrType>[0]

function build(id: Id, values: Record<string, string>): string {
  return findQrType(id).build(values)
}

function validate(id: Id, values: Record<string, string>): Record<string, string> {
  return { ...(findQrType(id).validate?.(values) ?? {}) }
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

  test('WPA3 only is T:WPA with transition disable, not T:SAE', () => {
    expect(build('wifi', { ssid: 'Cafe', password: 'p;w', encryption: 'WPA3' })).toBe(
      'WIFI:T:WPA;R:1;S:Cafe;P:p\\;w;;',
    )
  })

  test('WPA3 only and hidden combine', () => {
    expect(build('wifi', { ssid: 'Cafe', password: 'x', encryption: 'WPA3', hidden: 'true' })).toBe(
      'WIFI:T:WPA;R:1;S:Cafe;P:x;H:true;;',
    )
  })

  test('an unticked hidden box adds nothing', () => {
    expect(build('wifi', { ssid: 'Cafe', password: 'x', encryption: 'WPA', hidden: '' })).toBe(
      'WIFI:T:WPA;S:Cafe;P:x;;',
    )
  })
})

describe('MeCard', () => {
  test('writes every field in order with the double-semicolon terminator', () => {
    expect(
      build('mecard', {
        firstName: 'Ada',
        lastName: 'Lovelace',
        phone: '+15550100',
        email: 'ada@example.com',
        website: 'https://example.com',
        address: '12 Square, London',
        note: 'Met at the offsite',
      }),
    ).toBe(
      'MECARD:N:Lovelace,Ada;TEL:+15550100;EMAIL:ada@example.com;URL:https\\://example.com;' +
        'ADR:12 Square\\, London;NOTE:Met at the offsite;;',
    )
  })

  test('a lone name has no comma', () => {
    expect(build('mecard', { firstName: 'Ada' })).toBe('MECARD:N:Ada;;')
    expect(build('mecard', { lastName: 'Lovelace' })).toBe('MECARD:N:Lovelace;;')
  })

  test('escapes separators inside the name parts', () => {
    expect(build('mecard', { firstName: 'A;b', lastName: 'C,d' })).toBe('MECARD:N:C\\,d,A\\;b;;')
  })

  test('escapeMeCard escapes backslash, semicolon, comma and colon, and flattens newlines', () => {
    expect(escapeMeCard('a\\b;c,d:e\nf\r\ng')).toBe('a\\\\b\\;c\\,d\\:e f g')
    expect(escapeMeCard('"quoted"')).toBe('"quoted"')
  })

  test('needs at least one name', () => {
    expect(build('mecard', { phone: '+15550100' })).toBe('')
  })
})

describe('app store', () => {
  test('links an App Store ID', () => {
    expect(build('appstore', { store: 'apple', appId: '1234567890' })).toBe('https://apps.apple.com/app/id1234567890')
  })

  test('drops a pasted id prefix', () => {
    expect(build('appstore', { store: 'apple', appId: 'id1234567890' })).toBe('https://apps.apple.com/app/id1234567890')
  })

  test('defaults to Apple', () => {
    expect(build('appstore', { appId: '42' })).toBe('https://apps.apple.com/app/id42')
  })

  test('links a Google Play package', () => {
    expect(build('appstore', { store: 'google', appId: 'com.example.my_app' })).toBe(
      'https://play.google.com/store/apps/details?id=com.example.my_app',
    )
  })

  test('rejects an Apple ID that is not digits', () => {
    expect(validate('appstore', { store: 'apple', appId: 'com.example.app' })).toHaveProperty('appId')
    expect(build('appstore', { store: 'apple', appId: 'com.example.app' })).toBe('')
  })

  test('rejects a Play ID that is not a package name', () => {
    for (const appId of ['example', '1com.example', 'com..example', 'com.example.', 'com.1example', 'com.ex-ample', '1234567890']) {
      expect(validate('appstore', { store: 'google', appId })).toHaveProperty('appId')
      expect(build('appstore', { store: 'google', appId })).toBe('')
    }
  })

  test('an empty ID is missing, not invalid', () => {
    expect(validate('appstore', { store: 'google' })).toEqual({})
    expect(build('appstore', { store: 'google' })).toBe('')
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

describe('EPC SEPA credit transfer', () => {
  test('matches the EPC069-12 version 1 example, written as version 2', () => {
    expect(
      build('epc', {
        bic: 'BHBLDEHHXXX',
        name: 'Franz Mustermänn',
        iban: 'DE71110220330123456789',
        amount: '12.3',
        purpose: 'GDDS',
        reference: 'RF18539007547034',
      }),
    ).toBe(
      [
        'BCD',
        '002',
        '1',
        'SCT',
        'BHBLDEHHXXX',
        'Franz Mustermänn',
        'DE71110220330123456789',
        'EUR12.30',
        'GDDS',
        'RF18539007547034',
      ].join('\n'),
    )
  })

  test('keeps empty lines before a later element and drops them after the last', () => {
    expect(
      build('epc', {
        name: "François D'Alsace S.A.",
        iban: 'FR14 2004 1010 0505 0001 3M02 606',
        amount: '12,3',
        text: 'Client:Marie Louise La Lune',
      }),
    ).toBe(
      [
        'BCD',
        '002',
        '1',
        'SCT',
        '',
        "François D'Alsace S.A.",
        'FR1420041010050500013M02606',
        'EUR12.30',
        '',
        '',
        'Client:Marie Louise La Lune',
      ].join('\n'),
    )
  })

  test('the shortest payment ends at the IBAN', () => {
    expect(build('epc', { name: 'Red Cross', iban: 'be72000000001616' })).toBe(
      'BCD\n002\n1\nSCT\n\nRed Cross\nBE72000000001616',
    )
  })

  test('keeps every line when only the beneficiary note is set', () => {
    expect(build('epc', { name: 'Red Cross', iban: 'BE72000000001616', information: 'Thanks!' })).toBe(
      'BCD\n002\n1\nSCT\n\nRed Cross\nBE72000000001616\n\n\n\n\nThanks!',
    )
  })

  test('flattens a line break so it cannot shift the fields', () => {
    expect(build('epc', { name: 'Red\nCross', iban: 'BE72000000001616', text: 'a\r\nb' })).toBe(
      'BCD\n002\n1\nSCT\n\nRed Cross\nBE72000000001616\n\n\n\na b',
    )
  })

  test('needs a name and an IBAN', () => {
    expect(build('epc', { name: 'Red Cross' })).toBe('')
    expect(build('epc', { iban: 'BE72000000001616' })).toBe('')
  })

  test('rejects an IBAN with a wrong check digit or shape', () => {
    expect(validate('epc', { name: 'X', iban: 'BE73000000001616' }).iban).toMatch(/check digits/)
    expect(validate('epc', { name: 'X', iban: 'BE72 0000' }).iban).toMatch(/country code/)
    expect(build('epc', { name: 'X', iban: 'BE73000000001616' })).toBe('')
  })

  test('validates the BIC and purpose code shapes', () => {
    const base = { name: 'X', iban: 'BE72000000001616' }
    expect(validate('epc', { ...base, bic: 'BPOTBEB1' })).toEqual({})
    expect(validate('epc', { ...base, bic: 'bhbl de hh xxx' })).toEqual({})
    expect(validate('epc', { ...base, bic: 'BPOTBE' })).toHaveProperty('bic')
    expect(validate('epc', { ...base, bic: '1POTBEB1' })).toHaveProperty('bic')
    expect(validate('epc', { ...base, purpose: 'CHA1' })).toHaveProperty('purpose')
    expect(validate('epc', { ...base, purpose: 'CH' })).toHaveProperty('purpose')
  })

  test('rejects amounts outside 0.01 to 999999999.99', () => {
    const base = { name: 'X', iban: 'BE72000000001616' }
    for (const amount of ['0', '0.00', '0.001', '1000000000', '-1', '1e3', '1,234.50', '12.']) {
      expect(validate('epc', { ...base, amount })).toHaveProperty('amount')
    }
    expect(build('epc', { ...base, amount: '999999999.99' })).toContain('\nEUR999999999.99')
    expect(build('epc', { ...base, amount: '0.01' })).toContain('\nEUR0.01')
  })

  test('epcAmount writes two decimals and no grouping', () => {
    expect(epcAmount('12')).toBe('12.00')
    expect(epcAmount('007.5')).toBe('7.50')
    expect(epcAmount('3,14')).toBe('3.14')
    expect(epcAmount('abc')).toBe('')
  })

  test('a reference and a message are mutually exclusive', () => {
    const values = { name: 'X', iban: 'BE72000000001616', reference: 'RF18539007547034', text: 'Invoice' }
    expect(validate('epc', values)).toHaveProperty('text')
    expect(build('epc', values)).toBe('')
  })

  test('checks an RF reference and limits any reference to 35 characters', () => {
    const base = { name: 'X', iban: 'BE72000000001616' }
    expect(validate('epc', { ...base, reference: 'RF19539007547034' }).reference).toMatch(/check digits/)
    expect(validate('epc', { ...base, reference: '1234567890' })).toEqual({})
    expect(validate('epc', { ...base, reference: '1'.repeat(36) })).toHaveProperty('reference')
    expect(validate('epc', { ...base, reference: '+++090/9337/55493+++' })).toHaveProperty('reference')
  })

  test('enforces the field lengths in characters', () => {
    const iban = 'BE72000000001616'
    expect(validate('epc', { name: 'é'.repeat(70), iban })).toEqual({})
    expect(validate('epc', { name: 'a'.repeat(71), iban })).toHaveProperty('name')
    expect(validate('epc', { name: 'X', iban, text: 'a'.repeat(141) })).toHaveProperty('text')
    expect(validate('epc', { name: 'X', iban, information: 'a'.repeat(71) })).toHaveProperty('information')
  })

  test('limits the whole payload to 331 bytes', () => {
    const values = { name: 'ü'.repeat(70), iban: 'BE72000000001616', text: 'ü'.repeat(100) }
    expect(validate('epc', values).payload).toMatch(/331/)
    expect(build('epc', values)).toBe('')

    const fits = { ...values, text: 'a'.repeat(140) }
    expect(validate('epc', fits)).toEqual({})
    expect(new TextEncoder().encode(build('epc', fits)).length).toBeLessThanOrEqual(331)
  })

  test('the IBAN check is mod 97', () => {
    expect(isValidIban('GB82 WEST 1234 5698 7654 32')).toBe(true)
    expect(isValidIban('GB82 WEST 1234 5698 7654 33')).toBe(false)
    expect(isValidIban('NO9386011117947')).toBe(true)
    expect(isValidIban('MT84MALT011000012345MTLCAST001S')).toBe(true)
    expect(mod97Valid('RF18539007547034')).toBe(true)
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
