# Authentication

Sign-in follows mailoverlord's design, so the two deploy the same way. `app.security.mode`
(or `APP_SECURITY_MODE`) picks one of four mechanisms; they are alternatives, not layers.

| Mode | Who signs in | Held by the app | Use when |
| --- | --- | --- | --- |
| `basic` (default) | The user, via HTTP Basic | Nothing: the credential rides every request | One shared password is enough |
| `oidc` | An identity provider, via the authorization code flow | A session cookie | You already run Okta or Entra ID |
| `header` | A reverse proxy in front of the app | Nothing: the proxy asserts the identity | The edge already authenticates |
| `none` | Nobody | Nothing | A machine nobody else can reach, and the test suite |

What is guarded is the same in every mode:

- **Public:** `/r/{code}`, because a phone scanning a printed code cannot sign in, plus
  `/actuator/health`, `/actuator/info` and `/v3/api-docs`.
- **Signed in (VIEWER):** everything else: the UI, rendering, the scan check and listing links.
- **OPERATOR:** creating, editing and deleting short links, and any other actuator endpoint.
  Saved designs follow their own rule: anyone signed in may save them, and a design can be
  changed by its owner, or by an operator when it is shared. See [the API](api.md#saved-designs).
  An OPERATOR is always a VIEWER too.

A refusal is an RFC 9457 problem document: 401 when nobody is signed in, 403 when the user lacks
the role (or, under OIDC, when a change arrives without its CSRF token). The UI hides the link
actions from a viewer, and shows who is signed in in the header.

## Basic

The `spring.security.user` identity, `operator` by default, is an OPERATOR. Hand the same
password to others with a narrower role by naming them:

```yaml
app:
  security:
    viewer-users: [alice, bob]     # render and list only
    operator-users: [carol]        # may change links too
```

A `fetch` never shows the browser's Basic prompt, so when the API answers 401 the UI sends the
browser to `/login`, a top-level navigation that does. `curl --user operator:password` works
against the API the same way.

## OIDC

Set `app.security.mode=oidc`, configure one client registration, and give the provider the
redirect URI `{base-url}/login/oauth2/code/{registrationId}`:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          okta:                    # or "entra" for Microsoft Entra ID
            client-id: ...
            client-secret: ...
            provider: okta
        provider:
          okta:
            issuer-uri: https://yourdomain.okta.com/oauth2/default
          # entra:
          #   issuer-uri: https://login.microsoftonline.com/{tenant-id}/v2.0
app:
  security:
    mode: oidc
    roles-claim: groups            # Okta default; use "roles" for Entra ID
    operator-groups: [qr-operators]
```

`issuer-uri` is the whole vendor difference: Spring reads the provider's discovery document from
it. For Entra ID, define the operator group under *App roles* so it lands in the `roles` claim.
The claim is read as an array or a single string and matched case-insensitively; anyone who can
sign in is at least a VIEWER.

The session is a cookie, so changes need a CSRF token. The server writes a readable `XSRF-TOKEN`
cookie and the UI echoes it as `X-XSRF-TOKEN`. Logout is `POST /logout` with the same token; it
ends the local session, not the provider's.

## Trusted header

Behind a proxy that signs people in (oauth2-proxy, nginx `auth_request`), the app can trust the
identity the proxy sets:

```yaml
app:
  security:
    mode: header
    header: X-Auth-Request-User
    groups-header: X-Auth-Request-Groups
    operator-groups: [qr-operators]
    trusted-proxies: [127.0.0.1/32]    # where the proxy connects from
server:
  forward-headers-strategy: framework
```

The headers are believed only on connections from `trusted-proxies`, judged by the socket's own
peer address rather than `X-Forwarded-For`. The same header from anywhere else gets a 401. The
proxy must overwrite the identity headers on every request, and the app should only be reachable
through it. Startup refuses `mode: header` without a header name and an allowlist. A 401 in this
mode names no login page, because the proxy owns sign-in, so the UI shows the reason instead of
reloading.
