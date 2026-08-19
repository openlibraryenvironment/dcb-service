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
