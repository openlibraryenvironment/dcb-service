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
import services.k_int.interaction.alma.AlmaApiClient;
import services.k_int.interaction.alma.types.error.AlmaError;
import services.k_int.interaction.alma.types.error.AlmaErrorResponse;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static services.k_int.utils.ReactorUtils.raiseError;

@Slf4j
@Secondary
@Prototype
public class AlmaApiClientImpl implements AlmaApiClient {

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
		log.debug("POST request (custom content type) to: {}", path);

		final URI baseUri = resolve(UriBuilder.of(path).build());
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

	private <T> Mono<T> request(HttpMethod method, String path,
		Object body, Class<T> responseType, Map<String, Object> queryParams) {

		log.info("Creating request for {} {}", method, path);

		final URI baseUri = resolve(UriBuilder.of(path).build());
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
						"HTTP " + resp.getStatus() + " for " + finalUri, resp));
				}
			});
	}

	private URI resolve(URI relativeURI) {
		return RelativeUriResolver.resolve(config.getBaseUrl(), relativeURI);
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

		log.debug("Starting exchange for - \n  Method: {}, \n  URI: {}, \n  argumentType: {}, \n  body: {}, \n  headers: {}",
			request.getMethod(), request.getUri(), argumentType, bodyJson, request.getHeaders().asMap());

		// So we can use the body in error messages
		String finalBodyJson = bodyJson;

		return Mono.from(httpClient.exchange(request, argumentType, Argument.of(HttpClientResponseException.class)))
			.flatMap(response -> {

				if (response.getBody().isPresent()) {
					log.debug("Response body: {}", response.getBody().get());
					return Mono.just(response);

				} else if (response.getBody().isEmpty() && argumentType.equalsType(Argument.of(Void.class))) {
					log.warn("Response body is empty for request to {} with expected type {}", request.getPath(), argumentType.getType().getSimpleName());
					return Mono.just(response);
				}

				else {
					String errorMsg = String.format("Response body is empty for request to %s with expected type %s",
						request.getPath(), argumentType.getType().getSimpleName());
					log.error(errorMsg);
					return Mono.error(new IllegalStateException(errorMsg));
				}
			})
			.onErrorResume(HttpClientResponseException.class, ex -> {
				HttpStatus status = ex.getStatus();
				Optional<String> responseBody = ex.getResponse().getBody(String.class);

				if (responseBody.isPresent()) {

					Optional<AlmaErrorResponse> almaError = Optional.empty();
					try {
						almaError = Optional.of(objectMapper.readValue(responseBody.get(), AlmaErrorResponse.class));
					} catch (Exception e) {
						log.error("Conversion service failed to convert response body to AlmaErrorResponse: {}", responseBody.get(), e);
					}

					if (almaError.isPresent()) {
						AlmaErrorResponse errorResponse = almaError.get();

						StringBuilder logMsg = new StringBuilder();
						logMsg.append(String.format("Alma API error for %s (HTTP %d)", request.getPath(), status.getCode()));
						if (errorResponse.getErrorList() != null && errorResponse.getErrorList().getError() != null) {
							for (AlmaError e : errorResponse.getErrorList().getError()) {
								logMsg.append(String.format("%n - [%s] %s", e.getErrorCode(), e.getErrorMessage()));
							}
						}
						log.error(logMsg.toString());
//						return Mono.error(new AlmaException("Alma API error", errorResponse, status));

						String requestUri = request.getUri().toString();

						// lets try to bubble up as much info as possible about the request error
						return raiseError(Problem.builder()
							.withTitle("Alma API Error")
							.withStatus(Status.valueOf(status.getCode()))
							.withDetail(requestUri)
							.with("Request Method", request.getMethod().name())
							.with("Request path", request.getPath())
							.with("Server name", request.getServerName() != null ? request.getServerName() : "Unknown")
							.with("Request Headers", request.getHeaders().asMap())
							.with("Request Body", finalBodyJson)
							.with("Alma Error response", errorResponse)
							.with("Raw Error Body", responseBody.get() != null ? responseBody.get() : "No body")
							.build());
					} else {
						log.error("Failed to convert error body to AlmaErrorResponse for request to {}", request.getPath());
					}
				}

				log.error("HTTP {} error for request to {}: {}", status.getCode(), request.getPath(), responseBody.orElse("No body"), ex);
				return Mono.error(ex); // fallback
			});
	}
}
