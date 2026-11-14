/**
 * A first look at a batch CSV in the browser, so the panel can say how many codes a file will
 * make before it is uploaded.
 *
 * <p>The server parses the file again and is the authority: this follows the same RFC 4180 rules
 * (quoted fields with commas, doubled quotes and line breaks; blank lines skipped) but only to
 * count rows and show the first few. A file it cannot read is left for the server to explain.
 */

/** Matches the server's limits. */
export const MAX_BATCH_ROWS = 500
export const MAX_BATCH_BYTES = 1024 * 1024

export interface CsvSummary {
  /** The header row's column names, trimmed. */
  columns: string[]
  /** Whether a `data` column was found, without regard to case. */
  hasData: boolean
  /** Data rows after the header, not counting blank lines. */
  rows: number
  /** The first few rows' payloads and requested file names. */
  sample: { data: string; filename: string }[]
}

/** The CSV the panel offers as a starting point. */
export const SAMPLE_CSV =
  'data,filename\r\n' +
  'https://example.com,example\r\n' +
  'https://example.com/menu,menu\r\n' +
  '"WIFI:T:WPA;S:Cafe;P:espresso;;",wifi\r\n'

/** Splits CSV text into records. Unclosed quotes run to the end, as there is no error to show. */
export function parseCsv(text: string): string[][] {
  const source = text.startsWith('﻿') ? text.slice(1) : text
  const records: string[][] = []
  let record: string[] = []
  let field = ''
  let quoted = false
  let fieldStarted = false
  let i = 0

  const endRecord = (): void => {
    record.push(field)
    // A line with nothing on it is a blank line, not a record with one empty field.
    if (!(record.length === 1 && record[0] === '' && !fieldStarted)) {
      records.push(record)
    }
    record = []
    field = ''
    fieldStarted = false
  }

  while (i < source.length) {
    const c = source[i]
    if (quoted) {
      if (c === '"' && source[i + 1] === '"') {
        field += '"'
        i += 2
        continue
      }
      if (c === '"') {
        quoted = false
      } else {
        field += c
      }
      i++
      continue
    }
    if (c === '"' && field === '') {
      quoted = true
      fieldStarted = true
    } else if (c === ',') {
      record.push(field)
      field = ''
      fieldStarted = true
    } else if (c === '\r' || c === '\n') {
      if (c === '\r' && source[i + 1] === '\n') {
        i++
      }
      endRecord()
    } else {
      field += c
      fieldStarted = true
    }
    i++
  }
  if (field !== '' || record.length > 0 || fieldStarted) {
    endRecord()
  }
  return records
}

export function summarizeCsv(text: string, sampleSize = 3): CsvSummary {
  const [header = [], ...rows] = parseCsv(text)
  const columns = header.map((name) => name.trim())
  const lower = columns.map((name) => name.toLowerCase())
  const dataIndex = lower.indexOf('data')
  const nameIndex = lower.indexOf('filename')
  return {
    columns,
    hasData: dataIndex >= 0,
    rows: rows.length,
    sample: rows.slice(0, sampleSize).map((row) => ({
      data: dataIndex >= 0 ? (row[dataIndex] ?? '') : '',
      filename: nameIndex >= 0 ? (row[nameIndex] ?? '') : '',
    })),
  }
}
