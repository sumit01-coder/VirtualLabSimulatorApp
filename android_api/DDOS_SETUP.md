# DDoS Monitor (IP tracking + auto-block)

This repo’s Android admin app calls `https://.../android_api/ddos.php`.

Server files added:

- `android_api/ddos_guard.php` — request logger + auto-block + deny blocked IPs (HTTP 429)
- `android_api/ddos_lib.php` — file-backed storage/helpers
- `android_api/ddos.php` — API used by the Admin app’s “DDoS Monitor” screen

## Enable tracking

Include this at the very top of every API endpoint you want to monitor:

```php
require_once __DIR__ . '/ddos_guard.php';
```

`android_api/app_update.php` already includes it as an example.

## Storage

State is stored in:

- `cache/ddos_state.json` (relative to `android_api/`)

Make sure `cache/` is writable by PHP.

## Auth (recommended)

Set an admin token so only the Admin app can query/modify the block list:

- `ADMIN_API_TOKEN` — required `Authorization: Bearer <token>`

If `ADMIN_API_TOKEN` is not set, `ddos.php` is not protected.

## Tuning (env vars)

- `DDOS_LIMIT_10S` (default `80`) — per-IP requests/10 seconds before auto-block
- `DDOS_LIMIT_60S` (default `300`) — per-IP requests/60 seconds before auto-block
- `DDOS_AUTOBLOCK_SECONDS` (default `3600`) — auto-block duration in seconds
- `DDOS_WHITELIST` — comma-separated IPs that should never be blocked (example: `1.2.3.4,5.6.7.8`)

## Notes

This is an application-level block list (HTTP 429), not a firewall rule.
For real attacks, use CDN/WAF + server rate limiting as the primary defense.

## Runtime controls

`ddos.php` now supports extra admin control actions without changing the existing Android app responses:

- `GET ddos.php?action=config`
- `POST ddos.php?action=update_config`
- `POST ddos.php?action=clear_recent`
- `POST ddos.php?action=unblock_all`

`update_config` accepts JSON fields such as:

- `enabled`
- `preset` = `relaxed` | `normal` | `strict` | `custom`
- `limit_10s`
- `limit_60s`
- `autoblock_seconds`
