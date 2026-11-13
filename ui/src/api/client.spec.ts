import { afterEach, describe, expect, test, vi } from 'vitest'
import { createLink, currentUser, listLinks } from './client'

/** A response with a text body, shaped like the ones the client reads. */
function response(status: number, body = '', contentType = 'application/problem+json'): Response {
  return new Response(body, { status, headers: { 'Content-Type': contentType } })
}

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
})

function stubNavigation(): ReturnType<typeof vi.fn> {
  const assign = vi.fn()
  vi.stubGlobal('location', { ...window.location, assign })
  return assign
}

describe('sign-in handling', () => {
  test('a 401 naming a login-url sends the browser there', async () => {
    const assign = stubNavigation()
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        response(401, JSON.stringify({ detail: 'Sign in.', 'login-url': '/oauth2/authorization/okta' })),
      ),
    )

    await expect(listLinks('', 0, 25)).rejects.toThrow('Sign in required.')
    expect(assign).toHaveBeenCalledWith('/oauth2/authorization/okta')
  })

  test('a 401 that is not ours goes to /login', async () => {
    const assign = stubNavigation()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response(401, '<html>proxy</html>', 'text/html')))

    await expect(currentUser()).rejects.toThrow('Sign in required.')
    expect(assign).toHaveBeenCalledWith('/login')
  })

  test('a header-mode 401 stays put and shows the reason', async () => {
    const assign = stubNavigation()
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(response(401, JSON.stringify({ detail: 'The proxy must set X-User.' }))),
    )

    await expect(currentUser()).rejects.toThrow('The proxy must set X-User.')
    expect(assign).not.toHaveBeenCalled()
  })

  test('a 403 surfaces the server detail', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(response(403, JSON.stringify({ detail: 'That needs the operator role.' }))),
    )

    await expect(createLink({ url: 'https://example.com', expiresAt: null })).rejects.toThrow(
      'That needs the operator role.',
    )
  })
})

describe('CSRF', () => {
  test('a change echoes the XSRF-TOKEN cookie in the header', async () => {
    document.cookie = 'XSRF-TOKEN=abc%2B123'
    const fetchMock = vi.fn().mockResolvedValue(response(201, '{}', 'application/json'))
    vi.stubGlobal('fetch', fetchMock)

    await createLink({ url: 'https://example.com', expiresAt: null })

    const init = fetchMock.mock.calls[0]?.[1] as RequestInit
    expect(init.headers).toMatchObject({ 'X-XSRF-TOKEN': 'abc+123', 'Content-Type': 'application/json' })
  })

  test('a read sends no token', async () => {
    document.cookie = 'XSRF-TOKEN=abc'
    const fetchMock = vi
      .fn()
      .mockResolvedValue(response(200, '{"items":[],"total":0,"page":0,"size":25}', 'application/json'))
    vi.stubGlobal('fetch', fetchMock)

    await listLinks('', 0, 25)

    const init = fetchMock.mock.calls[0]?.[1] as RequestInit
    expect(init.headers).not.toHaveProperty('X-XSRF-TOKEN')
  })
})
