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
import reactor.core.publisher.Mono;


import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static io.micronaut.http.MediaType.APPLICATION_FORM_URLENCODED;
import static services.k_int.utils.ReactorUtils.raiseError;

/** Koha Api Client Impl - based off Alma approach **/

@Slf4j
@Secondary
public class KohaApiClientImpl implements KohaApiClient {

	private final HttpClient httpClient;
	private final KohaClientConfig config; // Assuming you create a Koha version of AlmaClientConfig
	private final ObjectMapper objectMapper;

	// Reactive cache for the OAuth2 token
	private Mono<String> currentTokenMono;

	public KohaApiClientImpl() {
		throw new IllegalStateException();
	}

	@Creator
	public KohaApiClientImpl(@Parameter("hostLms") HostLms hostLms,
													 @Parameter("client") HttpClient httpClient,
													 ObjectMapper objectMapper) {
		this.httpClient = httpClient;
		this.objectMapper = objectMapper;
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
	public <T> Mono<T> post(String path, Object body, Class<T> responseType, Map<String, Object> queryParams, String contentType) {
		return request(HttpMethod.POST, path, body, responseType, queryParams, null, contentType);
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
	 * Token Manager: Creates the caching stream for the OAuth2 token. Experimental so let's see how it works
	 */
	private synchronized Mono<String> getValidToken() {
		if (currentTokenMono == null) {
			currentTokenMono = fetchNewToken()
				.cache(
					// Cache the token until 60 seconds before it actually expires
					response -> Duration.ofSeconds(response.getExpiresIn() - 60),
					error -> Duration.ZERO,
					() -> Duration.ZERO
				)
				.map(KohaAuthResponse::getAccessToken);
		}
		return currentTokenMono;
	}

	/**
	 *  POST request to /api/v1/oauth/token to get the new token
	 */
	private Mono<KohaAuthResponse> fetchNewToken() {
//		log.debug("Fetching new Koha OAuth token for HostLMS: {}", config.getHostLmsCode());
		// it would be good to have an identifier in here
		log.debug("Fetching new Koha OAuth token for HostLMS");

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

		Mono<MutableHttpRequest<?>> requestBuilderMono = isPublic
			? Mono.just(HttpRequest.create(method, finalUri.toString()))
			: getValidToken().map(token -> HttpRequest.create(method, finalUri.toString()).bearerAuth(token));

		return requestBuilderMono.flatMap(request -> {
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
	}

	private URI resolve(URI relativeURI) {
		return RelativeUriResolver.resolve(config.getApiUrl(), relativeURI);
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

		log.debug("Starting Koha exchange for - Method: {}, URI: {}, argumentType: {}", request.getMethod(), request.getUri(), argumentType);
		String finalBodyJson = bodyJson;

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

						return raiseError(Problem.builder()
							.withTitle("Koha API Error")
							.withStatus(Status.valueOf(status.getCode()))
							.withDetail(request.getUri().toString())
							.with("Request Method", request.getMethod().name())
							.with("Koha Error Code", errorResponse.getErrorCode())
							.with("Koha Error Message", errorResponse.getError())
							.with("Request Body", finalBodyJson)
							.build());
					}
				}
				return Mono.error(ex);
			});
	}
}
