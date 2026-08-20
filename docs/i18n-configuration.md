# Internationalization configuration

Cadentia uses the church instance configuration package as the source of truth
for the locale used by first-party user interfaces. Configure the locale once
on the deployed instance; the admin web application and Telegram bot then read
that effective runtime value from the API.

## Configuration setting

Set `instance.locale` in the church configuration package:

```json
{
  "instance": {
    "instanceId": "river-city-worship",
    "displayName": "River City Worship",
    "environment": "production",
    "region": "us-central1",
    "timezone": "America/Chicago",
    "locale": "es-MX",
    "supportContact": "support@example.org"
  }
}
```

The example is a fragment of a complete `church-config.v1` package. Keep all
required package sections in the final artifact. The locale value must match
the church configuration contract:

- Language tags use a language code followed by optional subtags, for example
  `en-US`, `es-MX`, or `pt-BR`.
- The package schema validates the syntax but does not guarantee that every
  application surface has a translation catalog for that language.
- `instance.locale` is required in the package schema. The API uses `en-US`
  only for local-development configuration created without a package.

## Loading the package

The API loads the package selected by `CADENTIA_CHURCH_CONFIG_PATH`, which maps
to `cadentia.church-config.path`:

```bash
export CADENTIA_CHURCH_CONFIG_PATH=/secure/config/river-city-worship/cadentia-church-package.json
```

Validate the package before promotion:

```bash
npm --workspace @cadentia/intent-contracts run validate:church-config -- \
  /secure/config/river-city-worship/cadentia-church-package.json \
  --app-version=0.1.0
```

Changing the package requires the normal reviewed artifact promotion and API
restart/reload process. Do not add a separate `VITE_*` locale variable for the
browser, and do not use `VITE_CADENTIA_CHURCH_INSTANCE_ID` to select a language;
that variable only selects the isolated church-instance context.

## Surface behavior

### Admin web

After authentication, the API returns the effective locale in `GET
/admin/session`. The admin web normalizes the language tag to its base language
and sets the document language accordingly. The current built-in catalogs are:

| Configured locale | Catalog | Behavior |
| --- | --- | --- |
| `en` or an English tag | English | English copy |
| `es` or a Spanish tag such as `es-MX` | Spanish | Spanish copy for translated shared UI |
| `pt` or a Portuguese tag such as `pt-BR` | Portuguese | Portuguese copy for translated shared UI |
| Any other valid tag | English | Safe English fallback |

The current implementation covers the shell, navigation, shared UI primitives,
and document language. Feature-screen-specific copy that has not yet been
migrated remains English. The admin instance-settings response also exposes the
effective value as `defaultLocale`; in production that value is read-only and
comes from the package. The local-development implementation allows an admin
override for testing.

### Telegram bot

Telegram responses use the church instance locale, normalized to the base
language. The current built-in catalogs are English, Spanish, and Portuguese,
with English fallback for unsupported languages. Telegram's incoming
`from.language_code` is retained as request metadata but does not override the
church configuration, so all users of a church instance receive consistent
system copy.

Core bot prompts, authorization messages, command acknowledgements, buttons,
and proposal labels are translated where catalog entries exist. Dynamic text
from backend data, such as catalog titles or audit references, can remain in
the language in which that data was stored.

## Change and verify procedure

1. Update `.instance.locale` in the reviewed church package.
2. Run the church-config validator and review the package diff.
3. Promote the same package artifact and restart/reload the API.
4. Verify an authorized `GET /admin/session` response contains the expected
   `locale` and that `GET /admin/instance-configuration` contains the expected
   `defaultLocale`.
5. Reload or redeploy the admin-web static artifact. If an old language remains,
   check `index.html` and CDN/browser cache behavior before changing code.
6. Send `/start` or `/help` to the Telegram bot from an authorized test account
   and verify that the response uses the configured catalog.

Use the target church instance identifier when making API checks. A successful
HTTP response from a different instance does not verify the target package.

## Troubleshooting

**The package fails validation.** Check that `instance.locale` is present and
uses the language-tag pattern accepted by `church-config.v1`. Also ensure the
full package remains valid; the locale cannot be added as an arbitrary
top-level setting.

**The admin web remains in English.** Confirm that the API loaded the expected
package and that `/admin/session` returns the target locale. A valid but
unsupported locale intentionally falls back to English. For a supported locale,
rebuild/reload the static artifact and inspect stale `index.html` or CDN cache.

**Telegram remains in English.** Confirm the API's effective instance locale and
the target church-instance header/configuration. Telegram user language settings
do not control Cadentia copy. The current catalog recognizes the `en`, `es`, and
`pt` language bases; other valid tags fall back to English.

**The admin settings page allows or rejects an edit unexpectedly.** The
production contract treats the package as authoritative and exposes settings as
read-only. The in-memory update path is intentionally limited to the
`local-development` instance and is not a production persistence mechanism.

## Related runbooks

- [Package governance and promotion](runbooks/adr-022-package-governance.md)
- [Admin interface operations](runbooks/adr-036-admin-interface-operations.md)
- [Telegram bot operations](runbooks/adr-035-telegram-bot-operations.md)
- [Admin web package README](../apps/admin-web/README.md)
