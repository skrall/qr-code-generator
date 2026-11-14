import type { ErrorCorrection } from '../api/client'

/**
 * How large a QR code a payload needs, computed the way the server's encoder does it.
 *
 * <p>The server encodes with ZXing 3.5's {@code Encoder.encode}, hints {@code CHARACTER_SET=UTF-8}
 * and no {@code QR_VERSION}, {@code QR_COMPACT} or {@code GS1_FORMAT}. This module follows that
 * path step by step, so the size shown while typing is the size the server will render, and
 * "too long" here is exactly the payload the server answers with a 400:
 *
 * <ol>
 *   <li>One mode for the whole payload ({@link chooseMode}); ZXing never splits into segments.</li>
 *   <li>In byte mode, an ECI header naming UTF-8 (assignment 26) precedes the mode indicator,
 *       because a charset hint is given: 4 + 8 bits. Numeric and alphanumeric get no ECI.</li>
 *   <li>The 4-bit mode indicator, then the character count, whose width depends on the version
 *       range (1–9, 10–26, 27–40). In byte mode the count is UTF-8 bytes, not characters.</li>
 *   <li>The version search of {@code recommendVersion}: a provisional version chosen with the
 *       count width of version 1, then the final one chosen with the provisional version's width.
 *       {@code willFit} rounds the bits up to whole codewords.</li>
 *   <li>{@code terminateBits} rejects the result if the bits at the final version's count width do
 *       not fit. The terminator itself is only written when there is room, so it never counts.</li>
 * </ol>
 *
 * <p>{@code capacity.fixture.json} is produced from ZXing itself by {@code CapacityParityTest} and
 * replayed through this module by {@code capacity.spec.ts}; regenerate it when ZXing is upgraded.
 */

export type QrMode = 'NUMERIC' | 'ALPHANUMERIC' | 'BYTE'

export const EC_LEVELS: readonly ErrorCorrection[] = ['LOW', 'MEDIUM', 'QUARTILE', 'HIGH']

export const MAX_VERSION = 40

/**
 * From this version on the scan check warns that modules get small (`ScanChecker`'s
 * "version >= 10" hint), so the readout warns at the same point.
 */
export const DENSE_VERSION = 10

/**
 * Data codewords per version (index 0 is version 1): total codewords minus the error-correction
 * codewords of all blocks, as ZXing's {@code Version} tables give them. The parity fixture carries
 * the same table read from ZXing, and the spec compares the two.
 */
export const DATA_CODEWORDS: Readonly<Record<ErrorCorrection, readonly number[]>> = {
  LOW: [
    19, 34, 55, 80, 108, 136, 156, 194, 232, 274, 324, 370, 428, 461, 523, 589, 647, 721, 795, 861,
    932, 1006, 1094, 1174, 1276, 1370, 1468, 1531, 1631, 1735, 1843, 1955, 2071, 2191, 2306, 2434,
    2566, 2702, 2812, 2956,
  ],
  MEDIUM: [
    16, 28, 44, 64, 86, 108, 124, 154, 182, 216, 254, 290, 334, 365, 415, 453, 507, 563, 627, 669,
    714, 782, 860, 914, 1000, 1062, 1128, 1193, 1267, 1373, 1455, 1541, 1631, 1725, 1812, 1914, 1992,
    2102, 2216, 2334,
  ],
  QUARTILE: [
    13, 22, 34, 48, 62, 76, 88, 110, 132, 154, 180, 206, 244, 261, 295, 325, 367, 397, 445, 485, 512,
    568, 614, 664, 718, 754, 808, 871, 911, 985, 1033, 1115, 1171, 1231, 1286, 1354, 1426, 1502, 1582,
    1666,
  ],
  HIGH: [
    9, 16, 26, 36, 46, 60, 66, 86, 100, 122, 140, 158, 180, 197, 223, 253, 283, 313, 341, 385, 406,
    442, 464, 514, 538, 596, 628, 661, 701, 745, 793, 845, 901, 961, 986, 1054, 1096, 1142, 1222,
    1276,
  ],
}

/** The mode indicator every segment starts with. */
const MODE_BITS = 4
/** ECI mode indicator plus an 8-bit assignment number (UTF-8 is 26, below 128). */
const ECI_BITS = 4 + 8

/** Character count widths for versions 1–9, 10–26 and 27–40 (ISO/IEC 18004 table 3). */
const COUNT_BITS: Record<QrMode, readonly [number, number, number]> = {
  NUMERIC: [10, 12, 14],
  ALPHANUMERIC: [9, 11, 13],
  BYTE: [8, 16, 16],
}

/** The 45 characters of alphanumeric mode: digits, upper-case letters and ` $%*+-./:`. */
const ALPHANUMERIC = /^[0-9A-Z $%*+\-./:]$/

/** The side of the symbol in modules, without a quiet zone. */
export function modulesFor(version: number): number {
  return 17 + 4 * version
}

/** Data capacity of a version in codewords (bytes). */
export function dataCodewords(version: number, level: ErrorCorrection): number {
  return DATA_CODEWORDS[level][version - 1]
}

/**
 * ZXing's {@code chooseMode} without a Shift_JIS hint: numeric if every character is a digit,
 * alphanumeric if every character is in the alphanumeric set, otherwise byte. Lower-case letters
 * therefore make a whole URL byte mode. An empty string is byte mode, as in ZXing.
 */
export function chooseMode(content: string): QrMode {
  let numeric = false
  let alphanumeric = false
  for (const char of content) {
    if (char >= '0' && char <= '9') {
      numeric = true
    } else if (ALPHANUMERIC.test(char)) {
      alphanumeric = true
    } else {
      return 'BYTE'
    }
  }
  if (alphanumeric) {
    return 'ALPHANUMERIC'
  }
  return numeric ? 'NUMERIC' : 'BYTE'
}

/**
 * The length of {@code content.getBytes(UTF_8)} in Java. That differs from `TextEncoder` in one
 * case: an unpaired surrogate becomes `?` (1 byte) in Java but U+FFFD (3 bytes) in the browser.
 */
export function utf8Length(content: string): number {
  let bytes = 0
  for (let i = 0; i < content.length; i++) {
    const unit = content.charCodeAt(i)
    if (unit < 0x80) {
      bytes += 1
    } else if (unit < 0x800) {
      bytes += 2
    } else if (unit >= 0xd800 && unit <= 0xdbff) {
      const next = content.charCodeAt(i + 1)
      if (next >= 0xdc00 && next <= 0xdfff) {
        bytes += 4
        i++
      } else {
        bytes += 1
      }
    } else if (unit >= 0xdc00 && unit <= 0xdfff) {
      bytes += 1
    } else {
      bytes += 3
    }
  }
  return bytes
}

/** Width of the character count field for a mode at a version. */
export function characterCountBits(mode: QrMode, version: number): number {
  const widths = COUNT_BITS[mode]
  return widths[version <= 9 ? 0 : version <= 26 ? 1 : 2]
}

/** Bits of the encoded characters alone (ZXing's {@code appendBytes}). */
export function segmentDataBits(content: string, mode: QrMode): number {
  switch (mode) {
    case 'NUMERIC': {
      // Three digits in 10 bits, a trailing pair in 7, a single digit in 4.
      const rest = content.length % 3
      return Math.floor(content.length / 3) * 10 + (rest === 2 ? 7 : rest === 1 ? 4 : 0)
    }
    case 'ALPHANUMERIC':
      // Two characters in 11 bits, a trailing one in 6.
      return Math.floor(content.length / 2) * 11 + (content.length % 2) * 6
    case 'BYTE':
      return utf8Length(content) * 8
  }
}

/** Bits before the count: the ECI header in byte mode, then the mode indicator. */
function headerBits(mode: QrMode): number {
  return (mode === 'BYTE' ? ECI_BITS : 0) + MODE_BITS
}

function smallestVersion(bits: number, level: ErrorCorrection): number | null {
  const codewords = Math.ceil(bits / 8)
  for (let version = 1; version <= MAX_VERSION; version++) {
    if (dataCodewords(version, level) >= codewords) {
      return version
    }
  }
  return null
}

export interface QrSize {
  mode: QrMode
  /** UTF-8 bytes of the payload, as Java counts them. */
  bytes: number
  /** The version ZXing encodes at, or null when the payload is too long for version 40. */
  version: number | null
  /** Side of the symbol in modules, or null when too long. */
  modules: number | null
  /**
   * Codewords the header and data take at {@link version} (at version 40 when too long). The
   * terminator and padding fill the rest and are not counted.
   */
  usedCodewords: number
  /** Data codewords {@link version} holds (version 40's when too long). */
  capacityCodewords: number
}

/** Sizes a payload at an error-correction level exactly as the server's ZXing encoder would. */
export function measure(content: string, level: ErrorCorrection): QrSize {
  const mode = chooseMode(content)
  const bytes = utf8Length(content)
  const fixedBits = headerBits(mode) + segmentDataBits(content, mode)
  const count = mode === 'BYTE' ? bytes : content.length
  const bitsAt = (version: number) => fixedBits + characterCountBits(mode, version)

  const tooLong = (): QrSize => ({
    mode,
    bytes,
    version: null,
    modules: null,
    usedCodewords: Math.ceil(bitsAt(MAX_VERSION) / 8),
    capacityCodewords: dataCodewords(MAX_VERSION, level),
  })

  // recommendVersion: guess with version 1's count width, then settle with the guess's width.
  const provisional = smallestVersion(bitsAt(1), level)
  if (provisional === null) {
    return tooLong()
  }
  const version = smallestVersion(bitsAt(provisional), level)
  if (version === null) {
    return tooLong()
  }
  // appendLengthInfo and terminateBits re-check with the final version's own count width.
  const used = bitsAt(version)
  if (count >= 2 ** characterCountBits(mode, version) || used > dataCodewords(version, level) * 8) {
    return tooLong()
  }

  return {
    mode,
    bytes,
    version,
    modules: modulesFor(version),
    usedCodewords: Math.ceil(used / 8),
    capacityCodewords: dataCodewords(version, level),
  }
}
