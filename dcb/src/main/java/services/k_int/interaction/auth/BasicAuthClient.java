package services.k_int.interaction.auth;

import org.reactivestreams.Publisher;

import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.BasicAuth;
import io.micronaut.http.client.multipart.MultipartBody;

public interface BasicAuthClient {
	@SingleResult
	default Publisher<AuthToken> login(BasicAuth creds) {
		return login(creds, MultipartBody.builder().addPart("grant_type", "client_credentials").build());
	}

	// Basic auth is auto bound to the request.
	@SingleResult
	Publisher<AuthToken> login(BasicAuth creds, MultipartBody body);

	@SingleResult
	default Publisher<AuthToken> login(final String key, final String secret) {
		return login(new BasicAuth(key, secret));
	}
}
