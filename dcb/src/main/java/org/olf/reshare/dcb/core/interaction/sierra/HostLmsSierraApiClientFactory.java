package org.olf.reshare.dcb.core.interaction.sierra;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.olf.reshare.dcb.core.model.HostLms;
import org.reactivestreams.Publisher;

import io.micronaut.core.util.StringUtils;
import io.micronaut.http.BasicAuth;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.uri.UriBuilder;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;
import services.k_int.interaction.auth.AuthToken;
import services.k_int.interaction.sierra.SierraApiClient;
import services.k_int.interaction.sierra.bibs.BibResultSet;

@Singleton
public class HostLmsSierraApiClientFactory {

	private final HttpClient client;

	public HostLmsSierraApiClientFactory(HttpClient client) {
		this.client = client;
	}

	SierraApiClient createClientFor(final HostLms hostLms) {
		return new HostLmsSierraApiClient(hostLms, client);
	}

	public static class HostLmsSierraApiClient implements SierraApiClient {
		private static final String CLIENT_SECRET = "secret";
		private static final String CLIENT_KEY = "key";
		private static final String CLIENT_BASE_URL = "base-url";
		private AuthToken currentToken;
		private final URI rootUri;
		private final HostLms lms;
		private final HttpClient client;

		private HostLmsSierraApiClient(HostLms hostLms, HttpClient client) {
			URI hostUri = UriBuilder.of((String) hostLms.getClientConfig().get(CLIENT_BASE_URL)).build();
			rootUri = resolve(hostUri, UriBuilder.of("/iii/sierra-api/v6/").build());
			
			lms = hostLms;
			this.client = client;
		}

		@Override
		public Publisher<BibResultSet> bibs(Integer limit, Integer offset, String createdDate, String updatedDate,
				Iterable<String> fields, Boolean deleted, String deletedDate, Boolean suppressed, Iterable<String> locations) {

			return getRequest("bibs/")
				.map(req -> req.uri(theUri -> {
					theUri
						.queryParam("limit", limit)
						.queryParam("offset", offset)
						.queryParam("createdDate", createdDate)
						.queryParam("updatedDate", updatedDate)
						.queryParam("fields", iterableToArray(fields))
						.queryParam("deleted", deleted)
						.queryParam("deletedDate", deletedDate)
						.queryParam("suppressed", suppressed)
						.queryParam("locations", iterableToArray(locations))
						;
				}))
				.flatMap(this::ensureToken)
				.flatMap(req ->	Mono.from( client.retrieve(req, BibResultSet.class) ));
		}
		
		private <T> Object[] iterableToArray( Iterable<T> iterable ) {
			if (iterable == null) return null;
			final List<T> list = new ArrayList<T>();
			iterable.forEach(list::add);
			
			return list.size() > 0 ? list.toArray() : null;
		}

		@Override
		public Publisher<AuthToken> login(BasicAuth creds, MultipartBody body) {
			return postRequest("token")
					.map(req ->
						req.basicAuth(creds.getUsername(), creds.getPassword())
							.contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
							.body(body))
					.flatMap(req ->
						Mono.from( client.retrieve(req, AuthToken.class) ));
		}
		
		private <T> Mono<MutableHttpRequest<T>> ensureToken( MutableHttpRequest<T> request ) {
			
			return Mono.justOrEmpty(currentToken)
			  .filter( token -> !currentToken.isExpired() )
				.switchIfEmpty(	aquireAccessToken() )
				
				.map( validToken -> {
					
					final String token = validToken.toString();
					log.debug("Using Auth token: {}", token);
					
					return request.header(HttpHeaders.AUTHORIZATION, token);
				})
				.defaultIfEmpty(request);
		}
		
		private Mono<AuthToken> aquireAccessToken() {
			
			final Map<String, Object> conf = lms.getClientConfig();
			final String key = (String) conf.get(CLIENT_KEY);
			final String secret = (String) conf.get(CLIENT_SECRET);
			return Mono.from(login(key, secret))
				.map( newToken -> {
					currentToken = newToken;
					return newToken;
				});
		}

		private <T> Mono<MutableHttpRequest<T>> getRequest(String uri) {
			return createRequest(HttpMethod.GET, uri);
		}

		private <T> Mono<MutableHttpRequest<T>> postRequest(String uri) {
			return createRequest(HttpMethod.POST, uri);
		}

		private <T> Mono<MutableHttpRequest<T>> createRequest(HttpMethod method, String uri) {
			return Mono.just(UriBuilder.of(uri).build()).map(this::resolve)
					.map(resolvedUri -> {
						MutableHttpRequest<T> req = HttpRequest.create(method, resolvedUri.toString());
						return req.accept(MediaType.APPLICATION_JSON);
					});
		}
		
		private URI resolve(URI relativeURI) {
			return resolve(rootUri, relativeURI);
		}

		private static URI resolve(URI baseUri, URI relativeURI) {
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
			String rawQuery = thisUri.getRawQuery();
			if (StringUtils.isNotEmpty(rawQuery)) {
				return thisUri.resolve(relativeURI + "?" + rawQuery);
			} else {
				return thisUri.resolve(relativeURI);
			}
		}

	}
}
