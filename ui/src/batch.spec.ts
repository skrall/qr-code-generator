import { describe, expect, test } from 'vitest'
import { parseCsv, SAMPLE_CSV, summarizeCsv } from './batch'

describe('parseCsv', () => {
  test('reads quoted fields with commas, doubled quotes and line breaks', () => {
    expect(parseCsv('data,filename\r\n"a,b","say ""hi"""\r\n"one\ntwo",x\n')).toEqual([
      ['data', 'filename'],
      ['a,b', 'say "hi"'],
      ['one\ntwo', 'x'],
    ])
  })

  test('skips blank lines and a byte order mark, and keeps a final line without a break', () => {
    expect(parseCsv('﻿data\n\n\r\nlast')).toEqual([['data'], ['last']])
  })

  test('keeps a row of empty fields', () => {
    expect(parseCsv('data,filename\n,\n""\n')).toEqual([['data', 'filename'], ['', ''], ['']])
  })
})

describe('summarizeCsv', () => {
  test('counts rows and samples the data and filename columns in any case', () => {
    const summary = summarizeCsv('Filename, DATA \nfirst,https://a\n,https://b\nthird,c\nfourth,d\n')

    expect(summary.hasData).toBe(true)
    expect(summary.columns).toEqual(['Filename', 'DATA'])
    expect(summary.rows).toBe(4)
    expect(summary.sample).toEqual([
      { data: 'https://a', filename: 'first' },
      { data: 'https://b', filename: '' },
      { data: 'c', filename: 'third' },
    ])
  })

  test('notices a missing data column and an empty file', () => {
    expect(summarizeCsv('url\nhttps://a\n').hasData).toBe(false)
    expect(summarizeCsv('')).toEqual({ columns: [], hasData: false, rows: 0, sample: [] })
  })

  test('the sample file is one the server accepts', () => {
    const summary = summarizeCsv(SAMPLE_CSV)

    expect(summary.hasData).toBe(true)
    expect(summary.rows).toBe(3)
    expect(summary.sample[2].data).toBe('WIFI:T:WPA;S:Cafe;P:espresso;;')
  })
})
