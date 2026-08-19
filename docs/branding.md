# Patron-facing branding

A consortium and each of its libraries can carry a patron-facing brand: a logo, a square
header icon, a landing background, a welcome line, and a default theme name. This document
covers **how the images get in**, because that is the part with a deployment decision in
it.

The text fields and the theme name have no such decision — they are columns on
`consortium` and `library`, set through the `updateConsortium` and `updateLibrary` GraphQL
mutations, validated by `BrandingValidator`.

## Three ways an image gets in

They are not alternatives to choose between once. A single deployment can use all three,
and the brand fields store a URL in every case.

| | How the image reaches the patron | What the deployment needs | `dcb.branding.assets.store` |
|---|---|---|---|
| **1. External URL** | The customer's own CDN or web host | Nothing | either value — this always works |
| **2. Upload** | DCB serves it from `/discovery/brand-assets/{key}` | Nothing — it goes in Postgres | `database` *(default)* |
| **3. Upload, fronted by a CDN** | A CDN caches DCB's route | A CDN in front of DCB | `database` |

**Option 1 always works and needs no configuration.** An administrator pastes an absolute
`https://` URL into the brand field; `BrandingValidator` accepts it; nothing else is
involved. A consortium with its own brand team and CDN is never made to re-upload.

**Setting `store: none` turns option 2 off.** The upload routes then do not exist — not
"exist and return an error", but are absent from the running service, because
`BrandAssetUploadController` and `BrandAssetServeController` are both
`@Requires(beans = BrandAssetStore.class)`. That is the setting for a deployment that would
rather not keep images in its database. Option 1 is unaffected.

**Uploads are stored in Postgres, and that is deliberate.** Requiring object storage made
the feature conditional on infrastructure a deployment may not have and a developer almost
certainly does not — a container, credentials, an endpoint override and an addressing mode
before anybody could exercise the route at all. Object storage remains a reasonable option
for estates that already run a bucket and is expected back as a third value for `store`; it
was the wrong thing to require of everybody.

`BrandingValidator` accepts exactly two shapes and refuses everything else:

- an absolute `http(s)` URL with a host, or
- a path under `/discovery/brand-assets/` **whose key is a 64-character hex digest and a
  known extension** — not merely a matching prefix, because a prefix test that can be
  walked out of with `../` is not a prefix test.

`data:`, `javascript:`, protocol-relative `//host/x` and every other site-relative path are
refused, each with a test in `BrandingValidatorTests`.

### Option 3 is a layer, not a third storage location

Option 3 is option 2 with a CDN in front. It needs no code and no configuration in DCB. The
serve route is built for it:

- the key is the SHA-256 of the content, so a URL can never mean two different images;
- the response carries `Cache-Control: public, max-age=31536000, immutable`;
- replacing an image produces a *different* URL, so no cache anywhere has to be purged.

Point the CDN at `/discovery/brand-assets/**` and nothing else changes.

## What uploads need

Nothing. Images are stored in the same Postgres everything else uses, so there is no
bucket, no credentials, no endpoint and no extra container — locally, in CI, or in a
deployment. That is most of the reason they are stored there.

| Setting | Default | |
|---|---|---|
| `DCB_BRANDING_ASSETS_STORE` | `database` | `none` removes the upload routes |
| `DCB_BRANDING_ASSETS_ORPHAN_GRACE` | `24h` | How long an unsaved upload is kept — see Orphans |

### Telling the admin apps whether uploads are available

`/info` carries `dcb.branding.assets.store`, anonymously, which is how DCB Admin decides
whether to render an upload control at all:

```bash
curl -s $DCB/info | jq .dcb.branding.assets.store    # "database" or "none"
```

Without it the forms cannot tell "uploads are off here" from "that upload failed" —
`store: none` removes the routes, and the resulting 404 carries no message for the form to
show, so an administrator would get a generic failure every time on a deployment that
turned uploads off on purpose. `BrandingCapabilityInfoTests` pins the key, because a key
nobody asserts is a key somebody renames.

## Run-book: verifying branding against a running DCB

The automated tests cover the pieces. This is the end-to-end check — the one that catches
a thing that only shows up when a real browser fetches a real image from a real deployment.

Run it after deploying to a new environment, and after any change to
`dcb.branding.assets.*`, the multipart settings, or `BrandingValidator`.

**What you need:** a running DCB, a bearer token for a user holding `ADMIN`,
`CONSORTIUM_ADMIN` or `LIBRARY_ADMIN`, and two image files — a PNG with transparency and
anything that is not an image. `curl` and `psql` for the checks that look underneath.

```bash
DCB=https://dcb.example.org
TOKEN=...                      # an admin bearer token
```

### 1. The service starts and branding is reachable

```bash
curl -s $DCB/discovery/consortium | jq .
```

**Expect** 200 and a JSON body. It is anonymous by design — a patron sees the consortium's
brand before signing in. If this 404s, DCB is running without the branding migrations.

### 2. The CDN route works with no upload at all

Set a consortium logo to an absolute URL through DCB Admin, or the `updateConsortium`
mutation, then repeat step 1.

**Expect** `brandLogoUrl` echoed back exactly as entered. This path needs no configuration
and must work even when `store: none`.

### 3. Bad URLs are refused

Try each of these as a logo URL:

| Value | Expect |
|---|---|
| `javascript:alert(1)` | 400 |
| `data:image/png;base64,iVBORw0KGgo=` | 400 |
| `//evil.example.org/logo.png` | 400 |
| `/discovery/brand-assets/../../etc/passwd` | 400 |
| `/discovery/brand-assets/deadbeef.png` | 400 — right prefix, wrong key shape |

**Expect** all five refused with a message naming the problem. A 500, or a success, is a
finding. These are rendered as `<img src>` on an anonymous page for every patron.

### 4. Upload succeeds and returns a site-relative URL

```bash
curl -s -X POST $DCB/brand-assets \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@logo.png" | jq .
```

**Expect** `{"url":"/discovery/brand-assets/<64 hex>.png","contentType":"image/png","bytes":N}`.

- The URL is a **64-character hex digest** plus an extension. Anything else means the key
  derivation changed and stored URLs will not validate.
- `contentType` **matches what you uploaded**. A PNG that comes back `image/jpeg` means the
  format-preserving re-encode has regressed.

If this returns 404, `store` is `none` or the upload routes did not load. If it returns a
bare `{"status":413}`, `micronaut.server.multipart.max-file-size` is below
`dcb.branding.assets.max-bytes` — see the note in `application.yml`.

### 5. Uploading the same file twice is idempotent

Repeat step 4 with the identical file.

**Expect** the **same URL**. Keys are content-addressed, so this must not create a second
row. Confirm underneath:

```sql
select count(*) from brand_asset where asset_key = '<the key>';   -- expect 1
```

### 6. A refusal explains itself

```bash
curl -s -X POST $DCB/brand-assets \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@not-an-image.txt" -o - -w '\n%{http_code}\n'
```

**Expect** 400 **and a body containing a sentence** — "the file is not a PNG or a JPEG…".
A bare `{"type":"about:blank","status":400}` means the error handler is not being reached,
and an administrator would be told only that something failed.

Rename an SVG to `.png` and repeat: **expect** the same refusal. The check is on magic
bytes, not the filename.

### 7. The image is served with the headers that make it safe

```bash
curl -sI $DCB/discovery/brand-assets/<key>
```

**Expect** all four:

| Header | Value |
|---|---|
| `Content-Type` | `image/png` |
| `X-Content-Type-Options` | `nosniff` |
| `Cache-Control` | `public, max-age=31536000, immutable` |
| `Content-Disposition` | `inline` |

`nosniff` matters more than usual: the object is user-supplied and served from the same
origin as the patron interface.

### 8. Serving is anonymous

Repeat step 7 **without** the `Authorization` header.

**Expect** 200. The sign-in page has to show the mark of the organisation asking for the
credential. If this 401s, the patron app renders a broken image before login.

### 9. Uploading is not anonymous

```bash
curl -s -o /dev/null -w '%{http_code}\n' -X POST $DCB/brand-assets -F "file=@logo.png"
```

**Expect** 401, 403 or 400 — anything but 2xx. Uploading is a mutation.

### 10. A traversal never reaches the store

```bash
curl -s -o /dev/null -w '%{http_code}\n' "$DCB/discovery/brand-assets/../../application.yml"
```

**Expect** 404. The key shape is checked before anything is looked up.

### 11. Save the URL, and confirm it renders

Put the step-4 URL into the consortium logo through DCB Admin, then load the patron app
signed out.

**Expect** the logo renders. This is the only step that proves the whole chain — validator,
store, serve route, and the frontend's own URL handling — actually joins up.

### 12. The orphan sweep keeps unsaved uploads bounded

Upload an image (step 4) and do **not** save it anywhere.

```sql
select asset_key, size_bytes, date_created from brand_asset order by date_created desc limit 5;
```

**Expect** the row present. It stays for `DCB_BRANDING_ASSETS_ORPHAN_GRACE` (24h by
default) and is then removed by the daily sweep — that window exists so the sweep cannot
delete an image an administrator is part-way through choosing.

To verify the sweep rather than wait for it, check that a **referenced** asset survives:
leave the environment a day, then confirm the asset from step 11 is still there and the
unsaved one from this step is not.

```sql
-- Should stay flat over time. Growth here means the sweep is not running.
select count(*), pg_size_pretty(sum(size_bytes)::bigint) from brand_asset;
```

### 13. Turning uploads off leaves the CDN route working

Set `DCB_BRANDING_ASSETS_STORE=none` and restart.

**Expect** `POST /brand-assets` and `GET /discovery/brand-assets/{key}` both 404 — the
routes are absent, not failing — while step 2 still works exactly as before. Existing rows
are untouched; set it back and they serve again.

## What is accepted, and what is not

PNG and JPEG, identified by **magic bytes** — never by the filename or the declared
`Content-Type`, both of which are chosen by whoever made the request.

**SVG is refused, and that is a decision rather than an omission.** An SVG is a
script-capable document, and one served from our own origin is stored XSS in the chrome of
every page a patron sees, including the sign-in page. Sanitising was considered and
rejected: a sanitiser is a moving allow-list against a format that keeps growing script
surfaces. A brand pack that is SVG-only exports a PNG at 2x, which it already does for
every social platform.

**WebP is refused** because the JDK ships no WebP codec, so accepting it would mean storing
bytes we could not decode — the one case where the re-encode rule below is suspended, and
precisely the case where it matters most.

Every accepted image is **decoded and written back out** before storage, so what is served
is what a decoder produced rather than the bytes that arrived. That drops EXIF, colour
profiles, trailing payloads and every polyglot trick that depends on a parser reading past
the end of the image. The format is preserved: a PNG stays a PNG, a JPEG stays a JPEG.

Limits, both configurable:

| | Default | Why |
|---|---|---|
| `DCB_BRANDING_ASSETS_MAX_BYTES` | 2 MB | Generous for a mark and a landing background; small enough that uploading is not a way to fill a bucket |
| `DCB_BRANDING_ASSETS_MAX_DIMENSION` | 4096 px | A decompression-bomb limit, checked from the image header before a single pixel is decoded. A 40 KB PNG can declare 30000x30000 and cost 3.6 GB of heap the moment something decodes it |

`micronaut.server.multipart.max-file-size` is pinned to the same variable as
`max-bytes` in `application.yml`, and that matters: the framework default is 1 MB, and left
unpinned it would silently override the 2 MB cap and refuse the upload *before routing* —
where the controller's error handler cannot add a sentence explaining why.

## Orphans

There are two ways an uploaded image stops being needed, and they have different answers.

**Replaced.** Keys are content-addressed, so pointing a brand field at a new image never
overwrites the old row — it writes a second and leaves the first. `BrandAssetCleanup`
removes the previous one when the field changes. It never touches an absolute URL (a
consortium's CDN is not ours to delete from) and it never fails an update, because an
administrator whose new logo is live must not see an error about a stale row.

**Never saved.** This is the one that needs a sweep. An administrator uploads, receives a
URL, and saves it with a *separate* mutation — so an upload nobody saves is orphaned the
moment it lands, and the replace path never sees it. Without a sweep, an authenticated
administrator can insert unbounded 2 MB rows by uploading repeatedly and saving nothing.

`BrandAssetSweep` runs daily and deletes assets that no brand field refers to. **The
24-hour grace period is not a tuning knob**: between the upload and the mutation an asset is
legitimately unreferenced, and sweeping without a window would delete the image somebody is
part-way through choosing.

Being able to sweep at all is a consequence of storing images in the database. The
expensive part is knowing which keys are still referenced by any brand field on any row —
against a bucket that means enumerating objects and cross-checking them; here it is one
statement with two `NOT EXISTS` clauses.

## Switching storage later

Moving from external URLs to uploads is additive — existing absolute URLs keep working, and
the two can be mixed indefinitely.

Setting `store: none` after assets exist leaves the rows in place but removes the route
that serves them, so any brand field holding a `/discovery/brand-assets/` path will 404
until it is pointed at an absolute URL. Switch back and they work again; nothing is
deleted.

## Migration choices

The reasoning behind the schema, kept here rather than in the migration files. A comment
sitting next to `alter table` cannot be reviewed when the code it describes changes, and
one of these went stale a single commit after it was written.

Migrations: `V8_73_001` (consortium columns), `V8_73_002` (library columns), `V8_74_001`
(the `brand_asset` table), `V8_74_002` (the merge, below).

### Why the brand fields are their own columns

**One consortium migration, not two.** `V8_73_001` and a later `V8_73_003` added six
nullable columns to one table, split only because they were authored weeks apart. Neither
had run anywhere, so the boundary was still a free choice — it stops being one the moment a
migration is applied. Folded into one.

**A logo and a header icon are different assets, not one asset at two sizes.** A logo is a
lockup — a mark and a wordmark side by side, needing horizontal room. Dropped into a 32px
square it becomes an illegible smear. That is why `brand_header_icon_url` is a column and
not a rendering hint.

**A background is consortium-only.** A mark identifies an organisation and belongs at every
level of the brand chain; a canvas does not. A per-library background would repaint the
whole page every time a patron changed scope, which is motion rather than identity.

**No colour column.** An operator-typed hex can fail WCAG contrast and nothing would catch
it. `default_theme_name` names a theme from a registry whose every brand × mode pairing is
measured by a failing test — choosing from a tested list removes the failure mode instead of
mitigating it. Validated on write, tolerated on read: a name this build no longer ships
falls back to the default rather than white-screening the patron app.

**`patron_welcome` is not `description`.** That is prose about the consortium, written for
staff and shown in DCB Admin. This is patron-facing copy under the search box.

### `header_image_url` and `about_image_url`: merged, and the uploader columns dropped

`consortium` also carried `header_image_url` (rendered 36×36 in DCB Admin's app bar) and
`about_image_url` (48px tall on the landing card), each with an uploader name and an
uploader email address beside it. `V8_74_002` merges the URLs into the brand columns and
drops all six.

**The reason originally given for keeping them separate was wrong**, and is recorded here so
it is not repeated: it claimed a patron-facing mark is "a different asset at a different
size". It is not. symposia-ui renders its square mark at 28–32px and DCB Admin rendered
`header_image_url` at 36×36 — that is CSS, not a different image, and a consortium wanting
its mark to differ between its own apps is not a requirement anybody has. The second reason
was validation, and it went when both sets started passing through `BrandingValidator` on
create and on update.

So `header_image_url` became `brand_header_icon_url` — the square mark every app puts in its
header — and `about_image_url` became `brand_logo_url`, the larger mark, which is what a
login screen shows. `coalesce`, so a brand value already set wins.

**The uploader columns were the real reason to act.** `header_image_uploader` and
`header_image_uploader_email`, and their `about_` twins, held a member of staff's name and
email address on the consortium row. Three things were wrong with that at once:

- **the read surface.** `/graphql` requires `isAuthenticated()` and nothing more, and
  `getConsortia` had no role check, so any principal the realm issues a token to — including
  `DISCOVERY_SERVICE`, held by discovery backends that may be third party — could read
  `{ consortia { content { headerImageUploaderEmail } } }`. Both problems are fixed: the
  columns are gone and the fetcher now goes through `GraphQLRoles.require`;
- **no retention rule.** Nothing ever removed them. Clearing the image left the uploader
  behind, which is personal data outliving the only thing it described;
- **duplication.** DCB already logs who changes a consortium. The `audit_trigger` on this
  table writes the actor and the before/after into `data_change_log` for every column,
  including the brand ones, and that read *is* role-checked. `ConsortiumBrandProvenanceTests`
  pins it.

**Existing values are preserved, not discarded.** Before the drop, the migration writes one
`data_change_log` row per consortium holding all six values under `changes.old_values`, with
`change_category = 'Schema migration'`. Provenance therefore moves into the audit trail
rather than being lost — behind the role check, in the place provenance already lived. It
does mean the address is still stored, in a table with no retention policy of its own; that
is worth revisiting alongside `data_change_log` retention generally, and is not specific to
branding.

**This is a breaking GraphQL change.** `headerImageUrl`, `aboutImageUrl` and the four
uploader fields are gone from `type Consortium`, `UpdateConsortiumInput` and
`ConsortiumInput`. `ConsortiumInput` gains `brandHeaderIconUrl` and `brandLogoUrl` in their
place, so a consortium can still be created carrying a mark. dcb-admin-ui and
dcb-admin-for-libraries ship the consuming change on `feat/brand-upload-on-save`; deploy them
together.

### Why uploaded images live in Postgres

Requiring an S3-API bucket made uploading conditional on infrastructure a deployment may not
have and a developer almost certainly does not — a container, credentials, an endpoint
override and an addressing mode before anybody could exercise the route. Postgres is already
provisioned everywhere DCB runs, including in every test.

The size objection does not survive the numbers. Four columns can reference an uploaded
asset, so at 500 libraries the referenced set is 503 images, each capped at 2 MB before
storage — a bounded fraction of a database holding 20 million bibliographic records. What is
*not* bounded by those columns is uploads nobody saves, which is what the sweep is for.

Object storage remains a reasonable option for estates that already run a bucket and is
expected back as a third value for `dcb.branding.assets.store`.

### Details in `brand_asset` that look removable and are not

**`asset_key` is the SHA-256 of the content.** Rows are therefore written once and never
updated: identical bytes are the same key, different bytes are a different row. That is what
lets the served URL be immutable and cached for a year, why there is no version column, and
why `size_bytes` cannot drift from `bytes`.

**`storage external` on the `bytes` column.** PNG and JPEG are already compressed, so
Postgres would attempt pglz on every insert, fail to shrink anything, and store the value out
of line regardless. `EXTERNAL` skips the wasted attempt — same storage, less CPU on the one
path that writes two megabytes at a time.

**No index beyond the primary key.** Every read is by key and the sweep runs once daily; an
index on `date_created` would be maintained by every insert in order to serve one statement a
day.
