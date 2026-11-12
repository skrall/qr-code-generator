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
  | 'email'
  | 'phone'
  | 'sms'
  | 'wifi'
  | 'vcard'
  | 'event'
  | 'geo'
  | 'bitcoin'

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
}

export interface QrType {
  id: QrTypeId
  label: string
  hint: string
  fields: readonly QrField[]
  defaults: Readonly<Record<string, string>>
  build: (values: Readonly<Record<string, string>>) => string
}

type Values = Readonly<Record<string, string>>

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

function buildUrl(values: Values): string {
  const url = read(values, 'url')
  if (!url) {
    return ''
  }
  return /^[a-zA-Z][a-zA-Z0-9+.-]*:/.test(url) ? url : `https://${url}`
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
  const parts = [`T:${encryption}`, `S:${escapeWifi(ssid)}`]
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
          { value: 'WEP', label: 'WEP' },
          { value: 'nopass', label: 'Open network' },
        ],
      },
      { key: 'password', label: 'Password', kind: 'text', placeholder: 'Network password' },
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
