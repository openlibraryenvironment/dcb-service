package org.olf.dcb.core.interaction.koha;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.type.Argument;
import io.micronaut.http.*;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.serde.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.interaction.RelativeUriResolver;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.interaction.koha.dto.KohaAuthResponse;
import org.olf.dcb.core.interaction.koha.dto.KohaError;
import org.zalando.problem.Problem;
import org.zalando.problem.Status;
import org.zalando.problem.ThrowableProblem;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.net.URI;
import java.util.Objects;
import java.util.Map;
import java.util.Optional;

import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static io.micronaut.http.MediaType.APPLICATION_FORM_URLENCODED;
import static services.k_int.utils.ReactorUtils.raiseError;

/**
 * The Koha REST calls for one Host LMS.
 * <p>Every call outside {@code /api/v1/public/} carries a bearer token from
 * {@code POST /api/v1/oauth/token} (client credentials), held per Host LMS in
 * {@link KohaTokenCache}. A 401 drops the token and retries once with a new one, except on the
 * password validation, whose 401 is the patron's wrong password.
 * <p>{@code api-url} may end in {@code /api} or {@code /api/v1}, in any case, and may carry a path
 * prefix, which is kept and is part of the server's identity.
 * <p>A failure whose body fits Koha's error schema is raised as a problem carrying the status and
 * Koha's error and error code; any other failure is the raw {@code HttpClientResponseException}.
 * The request body never goes into a problem, because every problem parameter is copied into the
 * patron request's audit; a refused body reaches the debug log only, redacted on the token,
 * password-validation and patron calls.
 */

@Slf4j
@Secondary
public class KohaApiClientImpl implements KohaApiClient {

	private final HttpClient httpClient;
	private final KohaClientConfig config; // Assuming you create a Koha version of AlmaClientConfig
	private final ObjectMapper objectMapper;
	private final KohaTokenCache tokenCache;
	private final String hostLmsCode;

	public KohaApiClientImpl() {
		throw new IllegalStateException();
	}

	@Creator
	public KohaApiClientImpl(@Parameter("hostLms") HostLms hostLms,
		@Parameter("client") HttpClient httpClient,
		ObjectMapper objectMapper,
		KohaTokenCache tokenCache) {
		this.httpClient = httpClient;
		this.objectMapper = objectMapper;
		this.tokenCache = tokenCache;
		this.hostLmsCode = hostLms.getCode();
		this.config = new KohaClientConfig(hostLms);
	}

	@Override
	public <T> Mono<T> get(String path, Class<T> responseType, Map<String, Object> queryParams) {
		return request(HttpMethod.GET, path, null, responseType, queryParams, null, APPLICATION_JSON);
	}

	@Override
	public <T> Mono<T> get(String path, Class<T> responseType, Map<String, Object> queryParams, Map<String, String> headers) {
		return request(HttpMethod.GET, path, null, responseType, queryParams, headers, APPLICATION_JSON);
	}

	@Override
	public <T> Mono<T> post(String path, Object body, Class<T> responseType, Map<String, Object> queryParams) {
		return request(HttpMethod.POST, path, body, responseType, queryParams, null, APPLICATION_JSON);
	}

	@Override
	public <T> Mono<T> post(String path, Object body, Class<T> responseType, Map<String, Object> queryParams,
		Map<String, String> headers, String contentType) {
		return request(HttpMethod.POST, path, body, responseType, queryParams, headers, contentType);
	}

	@Override
	public <T> Mono<T> post(String path, Object body, Class<T> responseType, Map<String, Object> queryParams,
		Map<String, String> headers) {
		return request(HttpMethod.POST, path, body, responseType, queryParams, headers, APPLICATION_JSON);
	}

	@Override
	public <T> Mono<T> put(String path, Object body, Class<T> responseType, Map<String, Object> queryParams) {
		return request(HttpMethod.PUT, path, body, responseType, queryParams, null, APPLICATION_JSON);
	}

	@Override
	public <T> Mono<T> patch(String path, Object body, Class<T> responseType, Map<String, Object> queryParams) {
		return request(HttpMethod.PATCH, path, body, responseType, queryParams, null, APPLICATION_JSON);
	}

	@Override
	public Mono<Void> delete(String path, Map<String, Object> queryParams) {
		return request(HttpMethod.DELETE, path, null, Void.class, queryParams, null, APPLICATION_JSON);
	}

	/**
	 *  POST request to /api/v1/oauth/token to get the new token
	 */
	private Mono<KohaAuthResponse> fetchNewToken() {
		log.debug("Fetching new Koha OAuth token for {}", hostLmsCode);

		Map<String, String> formData = Map.of(
			"grant_type", "client_credentials",
			"client_id", config.getClientId(),
			"client_secret", config.getClientSecret()
		);

		URI finalUri = resolve(UriBuilder.of("/api/v1/oauth/token").build());
		MutableHttpRequest<?> request = HttpRequest.POST(finalUri, formData)
			.contentType(APPLICATION_FORM_URLENCODED)
			.accept(APPLICATION_JSON);

		return Mono.from(httpClient.retrieve(request, KohaAuthResponse.class))
			.doOnError(e -> log.error("Failed to fetch Koha OAuth token", e));
	}

	private <T> Mono<T> request(HttpMethod method, String path, Object body, Class<T> responseType, Map<String, Object> queryParams, Map<String, String> headers, String contentType) {
		log.info("Creating request for {} {}", method, path);

		final URI baseUri = resolve(UriBuilder.of(path).build());
		final UriBuilder uriBuilder = UriBuilder.of(baseUri);
		if (queryParams != null) queryParams.forEach(uriBuilder::queryParam);
		final URI finalUri = uriBuilder.build();

		// Check if endpoint is public
		boolean isPublic = path.contains("/api/v1/public/");

		// Deferred so that a retry after a refused token reads the cache again
		Mono<MutableHttpRequest<?>> requestBuilderMono = isPublic
			? Mono.just(HttpRequest.create(method, finalUri.toString()))
			: Mono.defer(() -> tokenCache.get(hostLmsCode, this::fetchNewToken))
				.map(token -> HttpRequest.create(method, finalUri.toString()).bearerAuth(token));

		final Mono<T> exchange = requestBuilderMono.flatMap(request -> {
			request.contentType(contentType).accept(APPLICATION_JSON);
			// Inject Custom Headers (e.g. x-koha-embed)
			if (headers != null) {
				headers.forEach(request::header);
			}
			if (body != null) request.body(body);

			return doExchange(request, Argument.of(responseType))
				.handle((resp, sink) -> {
					int code = resp.getStatus().getCode();
					if (code >= 200 && code < 300) {
						resp.getBody().ifPresentOrElse(sink::next, sink::complete);
					} else {
						sink.error(new HttpClientResponseException("HTTP " + resp.getStatus() + " for " + finalUri, resp));
					}
				});
		});

		return isPublic || carriesCredentials(path)
			? exchange
			: exchange.retryWhen(fetchTokenOnceMoreWhenRefused());
	}

	// A token Koha has dropped early (a restart, a key rotation) is otherwise reused until its
	// stated expiry. Safe for every method: a 401 means Koha did not act on the request. The
	// password validation is excluded because its 401 is the patron's wrong password
	private Retry fetchTokenOnceMoreWhenRefused() {
		return Retry.max(1)
			.filter(KohaApiClientImpl::isUnauthorised)
			.doBeforeRetry(signal -> {
				log.info("Koha refused the token for {}, fetching a new one and retrying once", hostLmsCode);
				tokenCache.invalidate(hostLmsCode);
			})
			.onRetryExhaustedThrow((spec, signal) -> signal.failure());
	}

	// doExchange raises a Problem when Koha's body fits its error schema, the raw exception otherwise
	private static boolean isUnauthorised(Throwable error) {
		if (error instanceof HttpClientResponseException response) {
			return response.getStatus() == HttpStatus.UNAUTHORIZED;
		}

		return error instanceof ThrowableProblem problem && problem.getStatus() == Status.UNAUTHORIZED;
	}

	// The token request carries the client secret and the validation request a patron's password
	private static boolean carriesCredentials(String path) {
		return path.endsWith("/oauth/token") || path.endsWith("/auth/password/validation");
	}

	// A patron body carries names and identifiers
	private static boolean carriesPersonalData(String path) {
		return carriesCredentials(path) || path.contains("/patrons");
	}

	private URI resolve(URI relativeURI) {
		return resolveBelow(config.getApiUrl(), relativeURI);
	}

	// RelativeUriResolver lets an absolute path replace the base path, which dropped the prefix
	// of a Koha served below one. A trailing /api or /api/v1, in any case, names the API root, not a
	// prefix: every path here starts with /api/v1
	static URI resolveBelow(URI apiUrl, URI apiPath) {
		final var prefix = Objects.requireNonNullElse(apiUrl.getRawPath(), "")
			.replaceAll("/+$", "")
			.replaceAll("(?i)/api(/v1)?$", "");

		return RelativeUriResolver.resolve(apiUrl, URI.create(prefix + apiPath));
	}

	private <T> Mono<HttpResponse<T>> doExchange(MutableHttpRequest<?> request, Argument<T> argumentType) {
		String bodyJson = "null";
		if (request.getBody().isPresent()) {
			try {
				bodyJson = objectMapper.writeValueAsString(request.getBody().get());
			} catch (Exception e) {
				bodyJson = "Failed to serialize: " + e.getMessage();
			}
		}

		// The path only: a query string carries a card number or userid
		log.debug("Starting Koha exchange for - Method: {}, path: {}, argumentType: {}", request.getMethod(), request.getPath(), argumentType);
		final String finalBodyJson = carriesPersonalData(request.getPath()) ? "[redacted]" : bodyJson;

		return Mono.from(httpClient.exchange(request, argumentType, Argument.of(HttpClientResponseException.class)))
			.flatMap(response -> {
				if (response.getBody().isPresent() || argumentType.equalsType(Argument.of(Void.class))) {
					return Mono.just(response);
				} else {
					return Mono.error(new IllegalStateException("Response body is empty for expected type " + argumentType.getType().getSimpleName()));
				}
			})
			.onErrorResume(HttpClientResponseException.class, ex -> {
				HttpStatus status = ex.getStatus();
				Optional<String> responseBody = ex.getResponse().getBody(String.class);

				if (responseBody.isPresent()) {
					Optional<KohaError> kohaError = Optional.empty();
					try {
						kohaError = Optional.of(objectMapper.readValue(responseBody.get(), KohaError.class));
					} catch (Exception e) {
						log.error("Failed to parse KohaError from response body", e);
					}

					if (kohaError.isPresent()) {
						KohaError errorResponse = kohaError.get();
						log.error("Koha API error for {} (HTTP {}): [{}] {}", request.getPath(), status.getCode(), errorResponse.getErrorCode(), errorResponse.getError());
						log.debug("Body of the refused {} {}: {}", request.getMethod(), request.getPath(), finalBodyJson);

						// No request body: every problem parameter is copied into the request's audit record

						return raiseError(Problem.builder()
							.withTitle("Koha API Error")
							.withStatus(Status.valueOf(status.getCode()))
							.withDetail(request.getPath())
							.with("Request Method", request.getMethod().name())
							.with("Koha Error Code", errorResponse.getErrorCode())
							.with("Koha Error Message", errorResponse.getError())
							.build());
					}
				}
				return Mono.error(ex);
			});
	}
}
