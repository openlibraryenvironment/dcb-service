package org.olf.dcb.core.interaction.polaris;

import static io.micronaut.http.HttpMethod.POST;
import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.olf.dcb.core.interaction.polaris.PolarisConstants.HMAC_SHA1_ALGORITHM;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.interaction.polaris.exceptions.PAPIAuthException;
import reactor.core.publisher.Mono;

@Slf4j
class PAPIAuthFilter {
	private final PolarisConfig polarisConfig;
	private AuthToken currentToken;
	private PatronAuthToken patronAuthToken;
	private final PolarisLmsClient client;
	private final String URI_PARAMETERS;
	private Boolean isPublicMethod;
	private final static DateTimeFormatter FORMATTER = DateTimeFormatter.RFC_1123_DATE_TIME
			.withZone(ZoneId.of("UTC"));

	PAPIAuthFilter(PolarisLmsClient client, PolarisConfig polarisConfig) {
		this.client = client;
		this.polarisConfig = polarisConfig;
		this.URI_PARAMETERS = polarisConfig.pAPIServiceUriParameters();
	}

	Mono<MutableHttpRequest<?>> ensureStaffAuth(MutableHttpRequest<?> request) {
		// By default, we assume any method requiring staff authentication is protected
		isPublicMethod = FALSE;

		return staffAuthentication(request, FALSE);
	}

	Mono<MutableHttpRequest<?>> ensurePatronAuth(MutableHttpRequest<?> request,
		PAPIClient.PatronCredentials patronCredentials, Boolean isRequestPublicMethod) {
		// Since some methods requiring patron authentication can also be authenticated by staff, we require this to be declared alongside the request
		isPublicMethod = isRequestPublicMethod;

		return patronAuthentication(request, patronCredentials, isRequestPublicMethod);
	}

	private Mono<MutableHttpRequest<?>> staffAuthentication(MutableHttpRequest<?> request, Boolean isRequestPublicMethod) {
		return staffAuthenticator().doOnSuccess(newToken -> currentToken = newToken)
			.map(validToken -> createStaffRequest(request, validToken, isRequestPublicMethod))
			.map(this::authorization);
	}

	private Mono<AuthToken> staffAuthenticator() {
		return Mono.defer(() -> {
			String domain = polarisConfig.getDomainId();
			String username = polarisConfig.getStaffUsername();
			String password = polarisConfig.getStaffPassword();

			return createStaffAuthRequest(domain, username, password)
				.flatMap(req -> client.retrieve(req, Argument.of(AuthToken.class)))
				.doOnSuccess(authToken -> log.info("Auth token returned: {}", authToken))
				.onErrorMap(e -> {
					log.error("Staff Auth failed with error {}", e.toString());
					return new PAPIAuthException("Staff Auth Failed", e);
				});
		});
	}

	private Mono<MutableHttpRequest<?>> patronAuthentication(MutableHttpRequest<?> request,
		PAPIClient.PatronCredentials patronCredentials, Boolean authenticatePatronAsStaff) {
		if (authenticatePatronAsStaff) {
			return staffAuthentication(request, TRUE);
		}

		if (patronCredentials.getBarcode() == null || patronCredentials.getPassword() == null) {
			log.debug("patronAuthentication with empty credentials: {}", request.getPath());

			return Mono.just(authorization(request));
		}

		return patronAuthenticator(patronCredentials)
			.doOnNext(newToken -> patronAuthToken = newToken)
			.map(token -> authorization(request));
	}

	private Mono<PatronAuthToken> patronAuthenticator(PAPIClient.PatronCredentials patronCredentials) {
		return Mono.defer(() -> createPatronAuthRequest(patronCredentials)
			.flatMap(request -> client.exchange(request, PatronAuthToken.class, TRUE))
			.map(HttpResponse::body))
			.doOnError(e -> Mono.error(new PAPIAuthException("Patron Auth Failed", e)));
	}

	private MutableHttpRequest<?> createStaffRequest(MutableHttpRequest<?> request,
		AuthToken authToken, Boolean isRequestPublicMethod) {

		final var token = authToken.getAccessToken();

		// When using a public method as authenticated staff user, we need to add a custom header
		if (isRequestPublicMethod) return request.header("X-PAPI-AccessToken", token);

		final var path = request.getPath().replace(URI_PARAMETERS, URI_PARAMETERS + "/" + token);

		return request.uri(uriBuilder -> uriBuilder.replacePath(path));
	}

	private Mono<MutableHttpRequest<?>> createStaffAuthRequest(String domain,
		String username, String password) {

		final var staffAuthUri = "/PAPIService/REST/protected" + URI_PARAMETERS + "/authenticator/staff";

		return createAuthRequest(staffAuthUri,
			StaffCredentials.builder()
				.Domain(domain)
				.Username(username)
				.Password(password)
				.build())
			.map(this::authorization);
	}

	private Mono<MutableHttpRequest<?>> createPatronAuthRequest(
		PAPIClient.PatronCredentials patronCredentials) {

		final var patronAuthUri = "/PAPIService/REST/public" + URI_PARAMETERS + "/authenticator/patron";

		return createAuthRequest(patronAuthUri, patronCredentials).map(this::authorization);
	}

	private Mono<MutableHttpRequest<?>> createAuthRequest(String path, Object requestBody) {
		return Mono.defer(() -> Mono.just(UriBuilder.of(path).build())
			.map(client::defaultResolve)
			.map(resolvedUri -> HttpRequest.create(POST, resolvedUri.toString()).accept(APPLICATION_JSON))
			.map(request -> request.body(requestBody)));
	}

	private MutableHttpRequest<?> authorization(MutableHttpRequest<?> request) {

		final var id = polarisConfig.getAccessId();
		final var key = polarisConfig.getAccessKey();
		final var method = request.getMethod().name();
		final var path = request.getUri().toString();
		final var date = generateUTCFormattedDateTime();
		final var accessSecret = getAccessSecret(request);

		final var signature = calculateApiSignature(key, method, path, date, accessSecret);
		final var token = "PWS " + id + ":" + signature;

		return request.header(HttpHeaders.AUTHORIZATION, token).header("PolarisDate", date);
	}

	private String getAccessSecret(MutableHttpRequest<?> request) {
		final var path = request.getPath();

		// Currently there is an instance in which the we can claim to be using a public method but still require staff auth
		// This conditional ensures that when hitting staff auth endpoint we still dont pass an access key
		if (isPublicMethod && path.contains("/authenticator/staff")) {
			return "";
		}

		else if (isPublicMethod) {
			return Optional.ofNullable(currentToken)
				.map(AuthToken::getAccessSecret)
				.orElse("");
		}

		else if (path.contains("protected") && !path.contains("/authenticator/staff")) {
			return Optional.ofNullable(currentToken)
				.map(AuthToken::getAccessSecret)
				.orElse("");
		}

		else if (path.contains("/authenticator/staff")) {
			return "";
		}

		else if (path.contains("public")) {
			return Optional.ofNullable(patronAuthToken)
				.map(PatronAuthToken::getAccessSecret)
				.orElse("");
		}

		else {
			log.warn("returning empty string to calculate api sig for request: {}", path);
			return "";
		}
	}

	private String calculateApiSignature(String accessKey, String method,
		String path, String date, String password) {
		try {
			final var mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
			final var secretBytes = accessKey.getBytes();
			final var signingKey = new SecretKeySpec(secretBytes, HMAC_SHA1_ALGORITHM);

			mac.init(signingKey);

			final var data = method + path + date + (password != null && !password.isEmpty() ? password : "");

			log.info("Encoding data: {}", data);

			final var rawHmac = mac.doFinal(data.getBytes());
			return Base64.getEncoder().encodeToString(rawHmac);
		} catch (Exception e) {
			// Handle any exceptions that might occur during the calculation
			throw new PAPIAuthException("Error calculating PAPI API signature", e);
		}
	}

	private String generateUTCFormattedDateTime() {
		return FORMATTER.format(Instant.now());
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	private static class PatronAuthToken {
		@JsonProperty("PAPIErrorCode")
		private Integer papiErrorCode;
		@JsonProperty("ErrorMessage")
		private String errorMessage;
		@JsonProperty("AccessToken")
		private String accessToken;
		@JsonProperty("AccessSecret")
		private String accessSecret;
		@JsonProperty("PatronID")
		private Integer patronID;
		@JsonProperty("AuthExpDate")
		private String authExpDate;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	private static class StaffCredentials {
		private String Domain;
		private String Username;
		private String Password;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	private static class AuthToken {
		@JsonProperty("PAPIErrorCode")
		private Integer papiErrorCode;
		@JsonProperty("ErrorMessage")
		private String errorMessage;
		@JsonProperty("AccessToken")
		private String accessToken;
		@JsonProperty("AccessSecret")
		private String accessSecret;
		@JsonProperty("PolarisUserID")
		private Integer polarisUserID;
		@JsonProperty("BranchID")
		private Integer branchID;
		@JsonProperty("AuthExpDate")
		private String authExpDate;
	}
}
