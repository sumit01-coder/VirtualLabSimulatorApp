# Android API Deploy Checklist

Upload these files to your server `android_api/` folder:

- `_common.php`
- `auth.php`
- `verify_otp.php`
- `dashboard.php`
- `tickets.php`
- `users.php`
- `settings.php`
- `updates.php`
- `departments.php`
- `labs.php`
- `remote_config.json`

Already existing and compatible:

- `app_update.php`
- `practicals.php`
- `ddos.php`
- `ddos_guard.php`
- `ddos_lib.php`

## Required server config

1. PHP 8+
2. DB connection via one of:
- existing `includes/db.php` that defines `$pdo`
- or env vars:
  - `DB_DSN`
  - `DB_USER`
  - `DB_PASS`
3. Strong token secret:
- `ADMIN_API_SECRET=your-long-random-secret`

## Optional config

- `MAINTENANCE_MODE=0|1` (fallback)
- `ADMIN_EMAIL_2FA=0|1` (fallback)
- `DDOS_LIMIT_10S`, `DDOS_LIMIT_60S`, `DDOS_AUTOBLOCK_SECONDS`, `DDOS_WHITELIST`

## Notes

- `settings.php` requires `super_admin` role in token.
- `tickets.php` supports:
  - `action=close`
  - `action=assign` with `assigned_admin` + `admin_note`
- `remote_config.json` is used by app Settings -> Sync remote config.

## Quick smoke test (replace domain)

```bash
curl -X POST https://your-domain.com/android_api/auth.php \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

If login returns token, test dashboard:

```bash
curl https://your-domain.com/android_api/dashboard.php \
  -H "Authorization: Bearer YOUR_TOKEN"
```