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

| | How the image reaches the patron | What the deployment needs | Set `dcb.branding.assets.bucket` |
|---|---|---|---|
| **1. External URL** | The customer's own CDN or web host | Nothing | No — leave blank |
| **2. Upload to object storage** | DCB serves it from `/discovery/brand-assets/{key}` | An S3-API bucket | Yes |
| **3. Upload, fronted by a CDN** | A CDN caches DCB's route | A bucket, plus a CDN in front of DCB | Yes |

**Option 1 is the default and needs no configuration at all.** With a blank bucket the
upload route does not exist — not "exists and returns an error", but is absent from the
running service, because `BrandAssetUploadController` and `BrandAssetServeController` are
both `@Requires(beans = BrandAssetStore.class)` and no store bean is created without a
bucket. An administrator pastes an absolute `https://` URL into the brand field and that is
the whole flow.

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

```
DCB_BRANDING_ASSETS_BUCKET      an S3-API bucket name. Blank (default) disables uploads.
AWS_ACCESS_KEY_ID               \
AWS_SECRET_ACCESS_KEY            > read by the AWS SDK's default provider chain, not by
AWS_REGION                      /  Micronaut - there is no property to set instead.
```

For MinIO, R2 or Ceph, also set the endpoint and addressing mode:

```yaml
aws:
  s3:
    path-style-access-enabled: true
  services:
    s3:
      endpoint-override: http://your-endpoint:9000
```

`path-style-access-enabled` is not optional against MinIO. The AWS SDK addresses buckets as
virtual hosts by default — `http://bucket.endpoint/` — which resolves to nothing, so
without it every call fails in a way that looks like a broken endpoint rather than a wrong
addressing mode.

### Locally

```bash
docker compose --profile brand-assets up -d      # in scripts/
export AWS_ACCESS_KEY_ID=dcb
export AWS_SECRET_ACCESS_KEY=dcbdcbdcb
export AWS_REGION=us-east-1
```

`application-development.yml` already carries the endpoint and the bucket name; the compose
file creates the bucket.

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

Keys are content-addressed, so replacing an asset never overwrites the old object; it
writes a second one and leaves the first. The policy is **delete-on-replace**:
`BrandAssetCleanup` removes the previous object when a brand field is pointed somewhere
else. It never touches an absolute URL — a consortium's CDN is not ours to delete from —
and it never fails an update, because an administrator whose new logo is live must not see
an error about a stale object.

There is no sweep, and that is deliberate: a sweep needs a scheduler, a way to know which
keys are still referenced by any brand field on any row, and somebody to notice when it
stops running.

## Switching storage later

Moving a deployment from external URLs to uploads is additive — existing absolute URLs keep
working. Moving *between* buckets is not automatic: stored brand URLs are site-relative
paths under `/discovery/brand-assets/`, so the objects have to be copied to the new bucket
or those URLs will 404. Content-addressed keys make that a straight copy, but nothing does
it for you.
