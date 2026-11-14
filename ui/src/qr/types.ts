/**
 * The payload formats the generator offers.
 *
 * <p>A QR code only carries a string. What makes a "WiFi code" different from a "vCard code" is
 * the grammar of that string, and every reader expects a particular one. Composing it here keeps
 * the server ignorant of the difference: it receives the finished payload and renders it.
 */

export type QrTypeId =
  | 'text'
  | 'url'
  | 'appstore'
  | 'shortlink'
  | 'email'
  | 'phone'
  | 'sms'
  | 'wifi'
  | 'vcard'
  | 'mecard'
  | 'event'
  | 'geo'
  | 'bitcoin'
  | 'epc'

export type FieldKind =
  | 'text'
  | 'textarea'
  | 'email'
  | 'tel'
  | 'url'
  | 'number'
  | 'datetime-local'
  | 'select'
  | 'checkbox'

export interface SelectOption {
  value: string
  label: string
}

export interface QrField {
  key: string
  label: string
  kind: FieldKind
  placeholder?: string
  required?: boolean
  options?: readonly SelectOption[]
  help?: string
  maxLength?: number
  autocomplete?: string
  /**
   * A value that grants access, such as a network password. A share link never carries it, even
   * when the sharer chooses to include the content.
   */
  secret?: boolean
}

/**
 * What is wrong with the values, keyed by field. A key that names no field is about the payload
 * as a whole. An empty required field is not a problem here: the form already lists it as still
 * needed, so this only reports values that were entered but cannot be encoded.
 */
export type Problems = Readonly<Record<string, string>>

export interface QrType {
  id: QrTypeId
  label: string
  hint: string
  fields: readonly QrField[]
  defaults: Readonly<Record<string, string>>
  /** The payload, or '' while a required value is missing or {@link validate} reports a problem. */
  build: (values: Readonly<Record<string, string>>) => string
  validate?: (values: Readonly<Record<string, string>>) => Problems
}

type Values = Readonly<Record<string, string>>

const NO_PROBLEMS: Problems = Object.freeze({})

function hasProblems(problems: Problems): boolean {
  return Object.keys(problems).length > 0
}

function read(values: Values, key: string): string {
  return (values[key] ?? '').trim()
}

function flag(values: Values, key: string): boolean {
  return values[key] === 'true'
}

/** Escapes the characters that terminate a field in the WIFI: grammar. */
export function escapeWifi(value: string): string {
  return value.replace(/([\\;,:"])/g, '\\$1')
}

/**
 * Escapes the characters that are special in a MECARD field (DoCoMo's grammar): `;` ends a field,
 * `:` separates the name from the value, `,` separates the parts of N and ADR, and `\` escapes.
 * MeCard has no newline escape, so line breaks become spaces.
 */
export function escapeMeCard(value: string): string {
  return value.replace(/\r?\n/g, ' ').replace(/([\\;,:])/g, '\\$1')
}

/** Escapes the characters that terminate a property in vCard 3.0. */
export function escapeVCard(value: string): string {
  return value
    .replace(/\\/g, '\\\\')
    .replace(/\r?\n/g, '\\n')
    .replace(/;/g, '\\;')
    .replace(/,/g, '\\,')
}

/** Escapes the characters that terminate a property in iCalendar. */
export function escapeIcal(value: string): string {
  return value
    .replace(/\\/g, '\\\\')
    .replace(/\r?\n/g, '\\n')
    .replace(/;/g, '\\;')
    .replace(/,/g, '\\,')
}

/** Turns a `datetime-local` value into the floating form iCalendar expects. */
export function icalDate(value: string): string {
  const compact = value.trim().replace(/[-:]/g, '')
  if (compact.length === 13) {
    // Seconds were not entered; iCalendar wants them.
    return `${compact}00`
  }
  return compact
}

/**
 * Adds https:// when the value has no scheme, matching how a browser treats a bare host.
 * The short link form uses the same rule so a typed `example.com` is stored as a real URL.
 */
export function normalizeUrl(raw: string): string {
  const url = raw.trim()
  if (!url) {
    return ''
  }
  return /^[a-zA-Z][a-zA-Z0-9+.-]*:/.test(url) ? url : `https://${url}`
}

function buildUrl(values: Values): string {
  return normalizeUrl(read(values, 'url'))
}

function buildEmail(values: Values): string {
  const to = read(values, 'to')
  if (!to) {
    return ''
  }
  const params: string[] = []
  const subject = read(values, 'subject')
  if (subject) {
    params.push(`subject=${encodeURIComponent(subject)}`)
  }
  const body = read(values, 'body')
  if (body) {
    params.push(`body=${encodeURIComponent(body)}`)
  }
  return params.length === 0 ? `mailto:${to}` : `mailto:${to}?${params.join('&')}`
}

function buildPhone(values: Values): string {
  const number = read(values, 'number').replace(/[^\d+]/g, '')
  return number ? `tel:${number}` : ''
}

function buildSms(values: Values): string {
  const number = read(values, 'number').replace(/[^\d+]/g, '')
  if (!number) {
    return ''
  }
  const message = read(values, 'message').replace(/\r?\n/g, ' ')
  return `SMSTO:${number}:${message}`
}

function buildWifi(values: Values): string {
  const ssid = read(values, 'ssid')
  if (!ssid) {
    return ''
  }
  const encryption = read(values, 'encryption') || 'WPA'
  // WPA3 has no T value of its own. The WPA3 specification (v3.1, section 7) defines T:WPA for
  // every password network, WPA2 and WPA3 alike, and adds R, a hex "transition disable" bitmap in
  // which 1 means WPA3-Personal only: a device that joins must not fall back to WPA2 later. So
  // "WPA3 only" is T:WPA;R:1, and the spec puts R right after T. T:SAE is not written: it is
  // Android's own spelling, absent from both the spec and ZXing's grammar, and other readers
  // reject it. The SAE-PK key (K) and password identifier (I) are left out as too rare to offer.
  const parts =
    encryption === 'WPA3'
      ? ['T:WPA', 'R:1', `S:${escapeWifi(ssid)}`]
      : [`T:${encryption}`, `S:${escapeWifi(ssid)}`]
  if (encryption !== 'nopass') {
    parts.push(`P:${escapeWifi(read(values, 'password'))}`)
  }
  if (flag(values, 'hidden')) {
    parts.push('H:true')
  }
  return `WIFI:${parts.join(';')};;`
}

function buildVCard(values: Values): string {
  const first = read(values, 'firstName')
  const last = read(values, 'lastName')
  if (!first && !last) {
    return ''
  }

  const lines = ['BEGIN:VCARD', 'VERSION:3.0', `N:${escapeVCard(last)};${escapeVCard(first)};;;`]
  const formatted = [first, last].filter(Boolean).join(' ')
  lines.push(`FN:${escapeVCard(formatted)}`)

  const org = read(values, 'org')
  if (org) {
    lines.push(`ORG:${escapeVCard(org)}`)
  }
  const title = read(values, 'title')
  if (title) {
    lines.push(`TITLE:${escapeVCard(title)}`)
  }
  const phone = read(values, 'phone')
  if (phone) {
    lines.push(`TEL;TYPE=CELL:${phone}`)
  }
  const email = read(values, 'email')
  if (email) {
    lines.push(`EMAIL;TYPE=INTERNET:${email}`)
  }
  const website = read(values, 'website')
  if (website) {
    lines.push(`URL:${website}`)
  }

  const street = read(values, 'street')
  const city = read(values, 'city')
  const region = read(values, 'region')
  const postalCode = read(values, 'postalCode')
  const country = read(values, 'country')
  if (street || city || region || postalCode || country) {
    lines.push(
      `ADR;TYPE=WORK:;;${escapeVCard(street)};${escapeVCard(city)};${escapeVCard(region)};` +
        `${escapeVCard(postalCode)};${escapeVCard(country)}`,
    )
  }

  const note = read(values, 'note')
  if (note) {
    lines.push(`NOTE:${escapeVCard(note)}`)
  }

  lines.push('END:VCARD')
  return lines.join('\r\n')
}

function buildEvent(values: Values): string {
  const summary = read(values, 'summary')
  const start = read(values, 'start')
  if (!summary || !start) {
    return ''
  }

  const lines = [
    'BEGIN:VCALENDAR',
    'VERSION:2.0',
    'PRODID:-//qr-code-generator//EN',
    'BEGIN:VEVENT',
    `SUMMARY:${escapeIcal(summary)}`,
    `DTSTART:${icalDate(start)}`,
  ]

  const end = read(values, 'end')
  if (end) {
    lines.push(`DTEND:${icalDate(end)}`)
  }
  const location = read(values, 'location')
  if (location) {
    lines.push(`LOCATION:${escapeIcal(location)}`)
  }
  const description = read(values, 'description')
  if (description) {
    lines.push(`DESCRIPTION:${escapeIcal(description)}`)
  }

  lines.push('END:VEVENT', 'END:VCALENDAR')
  return lines.join('\r\n')
}

function buildGeo(values: Values): string {
  const latitude = read(values, 'latitude')
  const longitude = read(values, 'longitude')
  return latitude && longitude ? `geo:${latitude},${longitude}` : ''
}

function buildBitcoin(values: Values): string {
  const address = read(values, 'address')
  if (!address) {
    return ''
  }
  const params: string[] = []
  const amount = read(values, 'amount')
  if (amount) {
    params.push(`amount=${encodeURIComponent(amount)}`)
  }
  const label = read(values, 'label')
  if (label) {
    params.push(`label=${encodeURIComponent(label)}`)
  }
  return params.length === 0 ? `bitcoin:${address}` : `bitcoin:${address}?${params.join('&')}`
}

function buildMeCard(values: Values): string {
  const first = read(values, 'firstName')
  const last = read(values, 'lastName')
  if (!first && !last) {
    return ''
  }

  // N is "Last,First"; readers swap the parts back. A lone name has no comma to swap on.
  const name = first && last ? `${escapeMeCard(last)},${escapeMeCard(first)}` : escapeMeCard(last || first)
  const fields = [`N:${name}`]
  const optional: readonly (readonly [string, string])[] = [
    ['TEL', 'phone'],
    ['EMAIL', 'email'],
    ['URL', 'website'],
    ['ADR', 'address'],
    ['NOTE', 'note'],
  ]
  for (const [property, key] of optional) {
    const value = read(values, key)
    if (value) {
      fields.push(`${property}:${escapeMeCard(value)}`)
    }
  }
  return `MECARD:${fields.join(';')};;`
}

/** An App Store ID is the number in an apps.apple.com link; a pasted `id` prefix is dropped. */
function appleAppId(raw: string): string {
  return raw.replace(/^id/i, '')
}

/** A Java package name with at least two segments, the shape Google Play requires. */
const PLAY_PACKAGE = /^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$/

function validateAppStore(values: Values): Problems {
  const id = read(values, 'appId')
  if (!id) {
    return NO_PROBLEMS
  }
  if (read(values, 'store') === 'google') {
    return PLAY_PACKAGE.test(id)
      ? NO_PROBLEMS
      : { appId: 'A Google Play ID is a package name, such as com.example.app.' }
  }
  return /^\d+$/.test(appleAppId(id))
    ? NO_PROBLEMS
    : { appId: 'An App Store ID is the number after "id" in the app\'s link, such as 1234567890.' }
}

function buildAppStore(values: Values): string {
  const id = read(values, 'appId')
  if (!id || hasProblems(validateAppStore(values))) {
    return ''
  }
  return read(values, 'store') === 'google'
    ? `https://play.google.com/store/apps/details?id=${id}`
    : `https://apps.apple.com/app/id${appleAppId(id)}`
}

/** Removes spaces and upper-cases, so an IBAN or BIC can be typed in its printed groups. */
function compact(value: string): string {
  return value.replace(/\s+/g, '').toUpperCase()
}

/**
 * The ISO 7064 mod 97-10 check shared by IBANs (ISO 13616) and RF creditor references
 * (ISO 11649): move the first four characters to the end, turn letters into numbers (A=10 ...
 * Z=35) and the remainder by 97 must be 1. The remainder is taken digit by digit, since the
 * number is far beyond a double's precision.
 */
export function mod97Valid(value: string): boolean {
  const rearranged = value.slice(4) + value.slice(0, 4)
  let remainder = 0
  for (const char of rearranged) {
    const digits = /\d/.test(char) ? char : String(char.charCodeAt(0) - 55)
    for (const digit of digits) {
      remainder = (remainder * 10 + Number(digit)) % 97
    }
  }
  return remainder === 1
}

/** True for a well-formed IBAN whose check digits are right. */
export function isValidIban(raw: string): boolean {
  const iban = compact(raw)
  return /^[A-Z]{2}\d{2}[A-Z0-9]{11,30}$/.test(iban) && mod97Valid(iban)
}

/** 4 letters of bank, 2 of country, 2 of location, optionally 3 of branch (ISO 9362). */
const BIC = /^[A-Z]{4}[A-Z]{2}[A-Z0-9]{2}([A-Z0-9]{3})?$/

/**
 * Turns a typed euro amount into the EPC form, "12.30": a dot, two decimals, no grouping. A comma
 * is accepted as the decimal separator. Returns '' for anything that is not an amount of 0.01 to
 * 999999999.99, the range the EPC guidelines allow.
 */
export function epcAmount(raw: string): string {
  const match = /^(\d{1,9})(?:[.,](\d{1,2}))?$/.exec(raw.trim())
  if (!match) {
    return ''
  }
  const euros = String(Number(match[1]))
  const cents = (match[2] ?? '').padEnd(2, '0')
  return euros === '0' && cents === '00' ? '' : `${euros}.${cents}`
}

/** Characters as a reader counts them, not UTF-16 code units. */
function length(value: string): number {
  return [...value].length
}

/** An EPC field is one line, so a pasted line break would shift every field after it. */
function epcText(values: Values, key: string): string {
  return read(values, key).replace(/\s*\r?\n\s*/g, ' ')
}

const EPC_MAX_BYTES = 331

/**
 * The EPC069-12 (v3.0) "GiroCode" layout for a SEPA credit transfer, one element per line:
 *
 *   1  BCD            service tag
 *   2  002            version; v2 makes the BIC optional inside the EEA
 *   3  1              character set, 1 = UTF-8
 *   4  SCT            identification: SEPA credit transfer
 *   5  BIC            8 or 11 characters, optional in v2
 *   6  name           beneficiary, at most 70 characters
 *   7  IBAN           at most 34 characters
 *   8  EUR12.30       amount, 0.01 to 999999999.99, optional
 *   9  purpose        4-character ISO 20022 purpose code, optional
 *  10  reference      structured creditor reference, at most 35, optional } only one of
 *  11  text           unstructured remittance text, at most 140, optional } these two
 *  12  information    beneficiary to originator, at most 70, optional
 *
 * Lines are separated by LF, and the last populated element is not followed by a separator, so
 * trailing empty lines are dropped. The whole payload is at most 331 bytes.
 */
function epcLines(values: Values): string[] {
  const amount = read(values, 'amount')
  const lines = [
    'BCD',
    '002',
    '1',
    'SCT',
    compact(read(values, 'bic')),
    epcText(values, 'name'),
    compact(read(values, 'iban')),
    amount ? `EUR${epcAmount(amount)}` : '',
    compact(read(values, 'purpose')),
    compact(read(values, 'reference')),
    epcText(values, 'text'),
    epcText(values, 'information'),
  ]
  while (lines[lines.length - 1] === '') {
    lines.pop()
  }
  return lines
}

function validateEpc(values: Values): Problems {
  const problems: Record<string, string> = {}

  const name = epcText(values, 'name')
  if (length(name) > 70) {
    problems.name = 'The name can be at most 70 characters.'
  }
  const iban = read(values, 'iban')
  if (iban && !isValidIban(iban)) {
    problems.iban = /^[A-Z]{2}\d{2}[A-Z0-9]{11,30}$/.test(compact(iban))
      ? 'This IBAN\'s check digits are wrong. Check it for a typo.'
      : 'An IBAN is a country code, two check digits and up to 30 letters or digits.'
  }
  const bic = read(values, 'bic')
  if (bic && !BIC.test(compact(bic))) {
    problems.bic = 'A BIC is 8 or 11 letters and digits, such as BHBLDEHHXXX.'
  }
  const amount = read(values, 'amount')
  if (amount && !epcAmount(amount)) {
    problems.amount = 'Enter an amount from 0.01 to 999999999.99, with at most two decimals.'
  }
  const purpose = read(values, 'purpose')
  if (purpose && !/^[A-Z]{4}$/.test(compact(purpose))) {
    problems.purpose = 'A purpose code is 4 letters, such as GDDS.'
  }
  const reference = compact(read(values, 'reference'))
  if (reference) {
    if (!/^[A-Z0-9]{1,35}$/.test(reference)) {
      problems.reference = 'A reference is up to 35 letters and digits.'
    } else if (reference.startsWith('RF') && !mod97Valid(reference)) {
      problems.reference = 'This RF creditor reference\'s check digits are wrong.'
    }
  }
  const text = epcText(values, 'text')
  if (text && reference) {
    problems.text = 'Use either a reference or a message, not both.'
  } else if (length(text) > 140) {
    problems.text = 'The message can be at most 140 characters.'
  }
  if (length(epcText(values, 'information')) > 70) {
    problems.information = 'The note can be at most 70 characters.'
  }

  if (!hasProblems(problems)) {
    const bytes = new TextEncoder().encode(epcLines(values).join('\n')).length
    if (bytes > EPC_MAX_BYTES) {
      problems.payload = `The payment is ${bytes} bytes; banking apps read at most ${EPC_MAX_BYTES}. Shorten the text.`
    }
  }
  return problems
}

function buildEpc(values: Values): string {
  if (!read(values, 'name') || !read(values, 'iban') || hasProblems(validateEpc(values))) {
    return ''
  }
  return epcLines(values).join('\n')
}

export const QR_TYPES: readonly QrType[] = [
  {
    id: 'text',
    label: 'Text',
    hint: 'Anything a reader should show as plain text.',
    fields: [
      { key: 'text', label: 'Text', kind: 'textarea', required: true, placeholder: 'Hello from a QR code', maxLength: 1200 },
    ],
    defaults: {},
    build: (values) => values.text ?? '',
  },
  {
    id: 'url',
    label: 'Website',
    hint: 'Opens a link. A missing scheme becomes https://.',
    fields: [
      { key: 'url', label: 'URL', kind: 'url', required: true, placeholder: 'example.com/page', autocomplete: 'url' },
    ],
    defaults: {},
    build: buildUrl,
  },
  {
    id: 'appstore',
    label: 'App store',
    hint: 'Opens an app\'s page in the App Store or on Google Play.',
    fields: [
      {
        key: 'store',
        label: 'Store',
        kind: 'select',
        options: [
          { value: 'apple', label: 'Apple App Store' },
          { value: 'google', label: 'Google Play' },
        ],
      },
      {
        key: 'appId',
        label: 'App ID',
        kind: 'text',
        required: true,
        placeholder: '1234567890 or com.example.app',
        help: 'The number in an apps.apple.com link, or the package name in a Google Play link.',
      },
    ],
    defaults: { store: 'apple' },
    build: buildAppStore,
    validate: validateAppStore,
  },
  {
    id: 'shortlink',
    label: 'Short link',
    hint: 'Stores the destination and encodes a link that redirects to it.',
    // The payload is the URL the server returns, not something composed here, so this type has
    // no fields of its own. ShortLinkForm owns the create step and App holds the result.
    fields: [],
    defaults: {},
    build: () => '',
  },
  {
    id: 'email',
    label: 'Email',
    hint: 'Starts a message to the address, optionally pre-filled.',
    fields: [
      { key: 'to', label: 'To', kind: 'email', required: true, placeholder: 'hello@example.com', autocomplete: 'email' },
      { key: 'subject', label: 'Subject', kind: 'text', placeholder: 'Enquiry' },
      { key: 'body', label: 'Message', kind: 'textarea', placeholder: 'Pre-filled body' },
    ],
    defaults: {},
    build: buildEmail,
  },
  {
    id: 'phone',
    label: 'Phone',
    hint: 'Dials a number.',
    fields: [
      { key: 'number', label: 'Number', kind: 'tel', required: true, placeholder: '+1 555 0100', autocomplete: 'tel' },
    ],
    defaults: {},
    build: buildPhone,
  },
  {
    id: 'sms',
    label: 'SMS',
    hint: 'Starts a text message, optionally pre-filled.',
    fields: [
      { key: 'number', label: 'Number', kind: 'tel', required: true, placeholder: '+1 555 0100', autocomplete: 'tel' },
      { key: 'message', label: 'Message', kind: 'textarea', placeholder: 'Pre-filled message' },
    ],
    defaults: {},
    build: buildSms,
  },
  {
    id: 'wifi',
    label: 'WiFi',
    hint: 'Joins a network without typing the password.',
    fields: [
      { key: 'ssid', label: 'Network name', kind: 'text', required: true, placeholder: 'My network' },
      {
        key: 'encryption',
        label: 'Security',
        kind: 'select',
        options: [
          { value: 'WPA', label: 'WPA / WPA2 / WPA3' },
          { value: 'WPA3', label: 'WPA3 only' },
          { value: 'WEP', label: 'WEP' },
          { value: 'nopass', label: 'Open network' },
        ],
      },
      { key: 'password', label: 'Password', kind: 'text', placeholder: 'Network password', secret: true },
      { key: 'hidden', label: 'The network is hidden', kind: 'checkbox' },
    ],
    defaults: { encryption: 'WPA' },
    build: buildWifi,
  },
  {
    id: 'vcard',
    label: 'Contact',
    hint: 'Adds a person to the address book (vCard 3.0).',
    fields: [
      { key: 'firstName', label: 'First name', kind: 'text', placeholder: 'Ada' },
      { key: 'lastName', label: 'Last name', kind: 'text', placeholder: 'Lovelace' },
      { key: 'org', label: 'Organisation', kind: 'text', placeholder: 'Analytical Engines Ltd' },
      { key: 'title', label: 'Job title', kind: 'text', placeholder: 'Mathematician' },
      { key: 'phone', label: 'Phone', kind: 'tel', placeholder: '+1 555 0100' },
      { key: 'email', label: 'Email', kind: 'email', placeholder: 'ada@example.com' },
      { key: 'website', label: 'Website', kind: 'url', placeholder: 'https://example.com' },
      { key: 'street', label: 'Street', kind: 'text' },
      { key: 'city', label: 'City', kind: 'text' },
      { key: 'region', label: 'Region', kind: 'text' },
      { key: 'postalCode', label: 'Postal code', kind: 'text' },
      { key: 'country', label: 'Country', kind: 'text' },
      { key: 'note', label: 'Note', kind: 'textarea' },
    ],
    defaults: {},
    build: buildVCard,
  },
  {
    id: 'mecard',
    label: 'MeCard',
    hint: 'Adds a person to the address book in the compact MeCard format.',
    fields: [
      { key: 'firstName', label: 'First name', kind: 'text', placeholder: 'Ada' },
      { key: 'lastName', label: 'Last name', kind: 'text', placeholder: 'Lovelace' },
      { key: 'phone', label: 'Phone', kind: 'tel', placeholder: '+1 555 0100' },
      { key: 'email', label: 'Email', kind: 'email', placeholder: 'ada@example.com' },
      { key: 'website', label: 'Website', kind: 'url', placeholder: 'https://example.com' },
      { key: 'address', label: 'Address', kind: 'text', placeholder: '12 St James\'s Square, London' },
      { key: 'note', label: 'Note', kind: 'textarea' },
    ],
    defaults: {},
    build: buildMeCard,
  },
  {
    id: 'event',
    label: 'Event',
    hint: 'Adds a calendar entry (iCalendar).',
    fields: [
      { key: 'summary', label: 'Title', kind: 'text', required: true, placeholder: 'Team offsite' },
      { key: 'start', label: 'Starts', kind: 'datetime-local', required: true },
      { key: 'end', label: 'Ends', kind: 'datetime-local' },
      { key: 'location', label: 'Location', kind: 'text', placeholder: 'The office' },
      { key: 'description', label: 'Description', kind: 'textarea' },
    ],
    defaults: {},
    build: buildEvent,
  },
  {
    id: 'geo',
    label: 'Location',
    hint: 'Opens a map pin.',
    fields: [
      { key: 'latitude', label: 'Latitude', kind: 'number', required: true, placeholder: '51.5007' },
      { key: 'longitude', label: 'Longitude', kind: 'number', required: true, placeholder: '-0.1246' },
    ],
    defaults: {},
    build: buildGeo,
  },
  {
    id: 'bitcoin',
    label: 'Bitcoin',
    hint: 'Sends a payment to an address.',
    fields: [
      { key: 'address', label: 'Address', kind: 'text', required: true, placeholder: 'bc1q...' },
      { key: 'amount', label: 'Amount (BTC)', kind: 'text', placeholder: '0.001' },
      { key: 'label', label: 'Label', kind: 'text', placeholder: 'Coffee fund' },
    ],
    defaults: {},
    build: buildBitcoin,
  },
  {
    id: 'epc',
    label: 'SEPA payment',
    hint: 'Pre-fills a euro bank transfer in a banking app (EPC QR code, also called GiroCode).',
    fields: [
      { key: 'name', label: 'Beneficiary', kind: 'text', required: true, placeholder: 'Red Cross', maxLength: 70 },
      { key: 'iban', label: 'IBAN', kind: 'text', required: true, placeholder: 'BE72 0000 0000 1616', maxLength: 42 },
      {
        key: 'bic',
        label: 'BIC',
        kind: 'text',
        placeholder: 'BPOTBEB1',
        maxLength: 11,
        help: 'Optional within the EEA.',
      },
      { key: 'amount', label: 'Amount (EUR)', kind: 'text', placeholder: '12.30', maxLength: 13 },
      { key: 'purpose', label: 'Purpose code', kind: 'text', placeholder: 'CHAR', maxLength: 4 },
      {
        key: 'reference',
        label: 'Reference',
        kind: 'text',
        placeholder: 'RF18539007547034',
        maxLength: 35,
        help: 'A structured creditor reference. Use this or a message, not both.',
      },
      { key: 'text', label: 'Message', kind: 'text', placeholder: 'Invoice 2026-114', maxLength: 140 },
      {
        key: 'information',
        label: 'Note to the payer',
        kind: 'text',
        maxLength: 70,
        help: 'Shown in the payer\'s banking app; not sent with the payment.',
      },
    ],
    defaults: {},
    build: buildEpc,
    validate: validateEpc,
  },
]

export function findQrType(id: QrTypeId): QrType {
  const type = QR_TYPES.find((candidate) => candidate.id === id)
  if (!type) {
    throw new Error(`Unknown QR type: ${id}`)
  }
  return type
}

/** A fresh set of values for a type, with its defaults applied. */
export function initialValues(type: QrType): Record<string, string> {
  return { ...type.defaults }
}
