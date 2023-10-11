package org.olf.dcb.core.interaction;

import java.net.URI;
import java.net.URISyntaxException;

import io.micronaut.core.util.StringUtils;

public class RelativeUriResolver {
	public static URI resolve(URI baseUri, URI relativeUri) {
		URI thisUri = baseUri;

		// if the URI features credentials strip this out
		if (StringUtils.isNotEmpty(thisUri.getUserInfo())) {
			try {
				thisUri = new URI(thisUri.getScheme(), null, thisUri.getHost(), thisUri.getPort(), thisUri.getPath(),
					thisUri.getQuery(), thisUri.getFragment());
			} catch (URISyntaxException e) {
				throw new IllegalStateException("URI is invalid: " + e.getMessage(), e);
			}
		}

		final var rawQuery = thisUri.getRawQuery();

		if (StringUtils.isNotEmpty(rawQuery)) {
			return thisUri.resolve(relativeUri + "?" + rawQuery);
		} else {
			return thisUri.resolve(relativeUri);
		}
	}
}
