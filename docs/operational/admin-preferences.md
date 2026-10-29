# Staff preferences

A member of staff's display settings (colour scheme, text size, spacing, typeface, motion and
language), each admin app's own state, saved filters and watched requests can follow them between
browsers. The feature is optional and never stops DCB from starting.

## Turning it on and off

| Setting | Effect |
|---|---|
| `DCB_PREFERENCES_SECRET` set | On |
| `DCB_PREFERENCES_SECRET` not set | Off, with one warning at startup. Nothing is stored |
| `DCB_PREFERENCES_ENABLED=false` | Off, without a warning, secret or not. For a deployment that declines storing the data |

While off, `GET /preferences/availability` answers `{"available": false}` and every other
`/preferences` route answers 503 `PREFERENCES_UNAVAILABLE`. The admin apps then keep everything
in the browser, as they did before the feature, and hide watching.

The secret has no default. Each person's records are filed under
`base64url(HMAC-SHA256(secret, "u:" + sub))`, where `sub` is the subject claim of their sign-in
token. The database holds neither the subject nor the username, so it names nobody without the
secret. **Changing the secret orphans every stored record**: everyone's settings, saved filters
and watches reset, and the old rows are swept after a year.

## What is kept, and for how long

- Display settings and each app's state, at most 16 KB per app. Grids remember filters, sort,
  column visibility and page size (5 to 100), at most 50 grids per app.
- Up to 50 saved filters per person, on the request grids.
- Up to 200 watched requests per person.

No patron barcode, no text a patron or a member of staff wrote, and no quick filter text is
stored: a filter on a patron is kept as the patron's internal id. The service refuses anything
else with 400.

A person who has used neither admin app for a year is removed with their saved filters and
watches. A watch is also removed when its request no longer exists, or finished (completed,
cancelled, finalised or archived) more than 90 days ago. The sweep runs daily and does nothing
while the feature is off, so switching off does not delete anyone's settings.

## Removing everything

Switch the feature off, then remove the data:

```sql
TRUNCATE admin_preference CASCADE;
```

`CASCADE` takes saved filters and watches with it.

## Settings

| Setting | Default |
|---|---|
| `dcb.preferences.max-saved-views` | 50 |
| `dcb.preferences.max-watched` | 200 |
| `dcb.preferences.retention` | 365d |
| `dcb.preferences.finished-watch-retention` | 90d |
