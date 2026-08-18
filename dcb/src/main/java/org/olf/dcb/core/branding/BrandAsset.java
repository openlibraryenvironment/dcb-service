package org.olf.dcb.core.branding;

/**
 * One stored brand image: the bytes and the media type they are served as (R-17b).
 *
 * The media type is not the client's. It is decided by {@link BrandAssetValidator} from
 * what the bytes actually turned out to be after they were decoded and written back out,
 * which is the only version of it worth storing — a {@code Content-Type} header on an
 * upload is a claim by whoever made the request.
 */
public record BrandAsset(String contentType, byte[] bytes) {

	public int size() {
		return bytes.length;
	}
}
