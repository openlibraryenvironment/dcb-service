package org.olf.dcb.core.interaction.alma;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
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
import org.zalando.problem.Problem;
import org.zalando.problem.Status;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import services.k_int.interaction.alma.AlmaApiClient;
import services.k_int.interaction.alma.types.error.AlmaError;
import services.k_int.interaction.alma.types.error.AlmaErrorResponse;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static services.k_int.utils.ReactorUtils.raiseError;

@Slf4j
@Secondary
@Prototype
public class AlmaApiClientImpl implements AlmaApiClient {

	private static final String PER_SECOND_THRESHOLD = "PER_SECOND_THRESHOLD";

	// Alma refuses a call over its per-second threshold without processing it, so retrying cannot repeat a write
	private static final Retry PER_SECOND_THRESHOLD_RETRY = Retry.backoff(3, Duration.ofSeconds(1))
		.jitter(0.5)
		.filter(AlmaApiClientImpl::isPerSecondThreshold)
		.onRetryExhaustedThrow((spec, signal) -> signal.failure());

	private final HttpClient httpClient;
	private final AlmaClientConfig config;
	private final ObjectMapper objectMapper;

	public AlmaApiClientImpl() {
		// No args constructor needed for Micronaut bean
		// context to not freak out when deciding which bean of the interface type
		// implemented it should use. Even though this one is "Secondary" the
		// constructor
		// args are still found to not exist without this constructor.
		throw new IllegalStateException();
	}

	@Creator
	public AlmaApiClientImpl(@Parameter("hostLms") HostLms hostLms,
		@Parameter("client") HttpClient httpClient,
		ObjectMapper objectMapper) {

		this.httpClient = httpClient;
		this.objectMapper = objectMapper;
		this.config = new AlmaClientConfig(hostLms);
	}

	@Override
	public <T> Mono<T> get(String path, Class<T> responseType, Map<String, Object> queryParams) {
		return request(HttpMethod.GET, path, null, responseType, queryParams);
	}

	@Override
	public <T> Mono<T> post(String path, Object body, Class<T> responseType, Map<String, Object> queryParams) {
		return request(HttpMethod.POST, path, body, responseType, queryParams);
	}

	@Override
	public <T> Mono<T> post(String path, Object body, Class<T> responseType, Map<String, Object> queryParams, String contentType) {
		final URI baseUri = resolve(URI.create(path));
		final UriBuilder uriBuilder = UriBuilder.of(baseUri);
		if (queryParams != null) queryParams.forEach(uriBuilder::queryParam);
		final URI finalUri = uriBuilder.build();

		final String apiKey = "apikey " + config.getApiKey();

		MutableHttpRequest<?> request = HttpRequest.POST(finalUri, body)
			.contentType(contentType)
			.accept(APPLICATION_JSON)
			.header(HttpHeaders.AUTHORIZATION, apiKey);

		return doExchange(request, Argument.of(responseType))
			.map(response -> response.getBody().get());
	}

	@Override
	public <T> Mono<T> put(String path, Object body, Class<T> responseType, Map<String, Object> queryParams) {
		return request(HttpMethod.PUT, path, body, responseType, queryParams);
	}

	@Override
	public Mono<Void> delete(String path, Map<String, Object> queryParams) {
		return request(HttpMethod.DELETE, path, null, Void.class, queryParams);
	}

	@Override
	public Mono<Void> authenticateUser(String userId, String password) {
		final URI uri = UriBuilder.of(resolve(UriBuilder.of("/almaws/v1/users/{userId}")
				.expand(Map.<String, Object>of("userId", userId))))
			.queryParam("op", "auth")
			.build();

		final MutableHttpRequest<?> request = HttpRequest.POST(uri, "")
			.accept(APPLICATION_JSON)
			.header(HttpHeaders.AUTHORIZATION, "apikey " + config.getApiKey())
			// Alma also accepts the password as a query parameter, which would carry it into logs and error details
			.header("Exl-User-Pw", password);

		return Mono.from(httpClient.exchange(request)).then();
	}

	private <T> Mono<T> request(HttpMethod method, String path,
		Object body, Class<T> responseType, Map<String, Object> queryParams) {

		final URI baseUri = resolve(URI.create(path));
		final UriBuilder uriBuilder = UriBuilder.of(baseUri);

		if (queryParams != null) queryParams.forEach(uriBuilder::queryParam);

		final URI finalUri = uriBuilder.build();
		final String finalUriString = finalUri.toString();
		final String apiKey = "apikey " + config.getApiKey();

		MutableHttpRequest<?> request = HttpRequest.create(method, finalUriString)
			.accept(APPLICATION_JSON)
			.header(HttpHeaders.AUTHORIZATION, apiKey);

		if (body != null) request = request.body(body);

		return doExchange(request, Argument.of(responseType))
			.handle((resp, sink) -> {
				int code = resp.getStatus().getCode();
				if (code >= 200 && code < 300) {
					// If status is 2xx and body exists, emit it with sink.next.
					// If status is 2xx and body is absent, complete with no value. This covers 204 No Content cleanly.
					resp.getBody().ifPresentOrElse(sink::next, sink::complete);
				} else {
					// we should not get here but if we do be explicit about it
					sink.error(new HttpClientResponseException(
						"HTTP " + resp.getStatus() + " for " + redactedPath(finalUri.getPath()), resp));
				}
			});
	}

	private URI resolve(URI relativeURI) {
		return RelativeUriResolver.resolve(config.getBaseUrl(), relativeURI);
	}

	private <T> Mono<HttpResponse<T>> doExchange(MutableHttpRequest<?> request, Argument<T> argumentType) {
		log.debug("Alma {} {}", request.getMethod(), redactedPath(request.getPath()));

		return Mono.from(httpClient.exchange(request, argumentType, Argument.of(HttpClientResponseException.class)))
			.flatMap(response -> {

				if (response.getBody().isPresent()) {
					return Mono.just(response);

				} else if (response.getBody().isEmpty() && argumentType.equalsType(Argument.of(Void.class))) {
					return Mono.just(response);
				}

				else {
					String errorMsg = String.format("Response body is empty for request to %s with expected type %s",
						redactedPath(request.getPath()), argumentType.getType().getSimpleName());
					log.error(errorMsg);
					return Mono.error(new IllegalStateException(errorMsg));
				}
			})
			.onErrorResume(HttpClientResponseException.class, ex -> {
				HttpStatus status = ex.getStatus();
				Optional<AlmaErrorResponse> almaError = ex.getResponse().getBody(String.class)
					.flatMap(this::parseAlmaError);

				if (almaError.isPresent()) {
					AlmaErrorResponse errorResponse = almaError.get();

					StringBuilder logMsg = new StringBuilder();
					logMsg.append(String.format("Alma API error for %s (HTTP %d)", redactedPath(request.getPath()), status.getCode()));
					if (errorResponse.getErrorList() != null && errorResponse.getErrorList().getError() != null) {
						for (AlmaError e : errorResponse.getErrorList().getError()) {
							logMsg.append(String.format("%n - [%s]", e.getErrorCode()));
						}
					}
					log.error(logMsg.toString());

					// These parameters are copied into patron request audit data, so they carry no headers or bodies
					return raiseError(Problem.builder()
						.withTitle("Alma API Error")
						.withStatus(Status.valueOf(status.getCode()))
						.withDetail(request.getMethod().name() + " " + request.getPath())
						.with("Request Method", request.getMethod().name())
						.with("Request path", request.getPath())
						.with("Alma Error response", errorResponse)
						.build());
				}

				log.error("HTTP {} error for request to {}", status.getCode(), redactedPath(request.getPath()));
				return Mono.error(ex); // fallback
			})
			.retryWhen(PER_SECOND_THRESHOLD_RETRY);
	}

	private Optional<AlmaErrorResponse> parseAlmaError(String body) {
		try {
			return Optional.ofNullable(objectMapper.readValue(body, AlmaErrorResponse.class));
		} catch (Exception e) {
			return Optional.empty();
		}
	}

	private static String redactedPath(String path) {
		return path == null ? null : path.replaceFirst("/users/[^/]+", "/users/{user}");
	}

	private static boolean isPerSecondThreshold(Throwable error) {
		// A 429 whose body could not be read cannot be told apart from the daily threshold; three retries of that are cheap
		if (error instanceof HttpClientResponseException unparsed) {
			return unparsed.getStatus().getCode() == 429;
		}

		final AlmaErrorResponse response = AlmaHostLmsClient.extractAlmaErrors(error);

		return response != null && response.getErrorList() != null
			&& response.getErrorList().getError() != null
			&& response.getErrorList().getError().stream()
				.anyMatch(almaError -> PER_SECOND_THRESHOLD.equals(almaError.getErrorCode()));
	}
}
