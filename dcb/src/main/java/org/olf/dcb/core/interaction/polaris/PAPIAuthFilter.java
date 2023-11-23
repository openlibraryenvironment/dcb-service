package org.olf.dcb.core.interaction.polaris;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.micronaut.http.HttpMethod.POST;
import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.olf.dcb.core.interaction.polaris.PolarisConstants.*;
import static org.olf.dcb.core.interaction.polaris.PolarisLmsClient.PolarisClient.PAPIService;

class PAPIAuthFilter {
	static final Logger log = LoggerFactory.getLogger(PAPIAuthFilter.class);
	private AuthToken currentToken;
	private PatronAuthToken patronAuthToken;
	private final PolarisLmsClient client;
	private final Map<String, Object> conf;
	private final String URI_PARAMETERS;
	private Boolean overridemethod;
	PAPIAuthFilter(PolarisLmsClient client) {
		this.client = client;
		this.conf = client.getHostLms().getClientConfig();
		this.URI_PARAMETERS = client.getGeneralUriParameters(PAPIService);
	}

	Mono<MutableHttpRequest<?>> ensureStaffAuth(MutableHttpRequest<?> request) {

		overridemethod = FALSE;
		return staffAuthentication(request, FALSE);
	}

	Mono<MutableHttpRequest<?>> ensurePatronAuth(MutableHttpRequest<?> request,
		PAPIClient.PatronCredentials patronCredentials, Boolean override) {

		overridemethod = override;
		return patronAuthentication(request, patronCredentials, override);
	}

	private Mono<MutableHttpRequest<?>> staffAuthentication(MutableHttpRequest<?> request, Boolean publicMethod) {
		return staffAuthenticator().doOnSuccess(newToken -> currentToken = newToken)
			.map(validToken -> createStaffRequest(request, validToken, publicMethod))
			.map(this::authorization);
	}

	private Mono<AuthToken> staffAuthenticator() {
		return Mono.defer(() -> {
			String domain = (String) conf.get(DOMAIN_ID);
			String username = (String) conf.get(STAFF_USERNAME);
			String password = (String) conf.get(STAFF_PASSWORD);

			return createStaffAuthRequest(domain, username, password)
				.flatMap(req -> client.retrieve(req, Argument.of(AuthToken.class)));
		});
	}

	private Mono<MutableHttpRequest<?>> patronAuthentication(MutableHttpRequest<?> request,
		PAPIClient.PatronCredentials patronCredentials, Boolean override) {

		if (override) {
			//log.debug("staffAuthentication with public method: {}", request.getPath());
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
			.flatMap(request -> client.exchange(request, PatronAuthToken.class))
			.flatMap(response -> Mono.justOrEmpty(response.getBody())));
	}

	private MutableHttpRequest<?> createStaffRequest(MutableHttpRequest<?> request, AuthToken authToken,
		Boolean publicMethod) {
		String token = authToken.getAccessToken();

		// guard clause: we do not need to add the auth token to the request path on public methods
		if (publicMethod) return request.header("X-PAPI-AccessToken", token);

		String path = request.getPath().replace(URI_PARAMETERS, URI_PARAMETERS + "/" + token);
		return request.uri(uriBuilder -> uriBuilder.replacePath(path));
	}

	private Mono<MutableHttpRequest<?>> createStaffAuthRequest(String domain, String username, String password) {
		String staffAuthUri = "/PAPIService/REST/protected" + URI_PARAMETERS + "/authenticator/staff";
		return createAuthRequest(staffAuthUri, StaffCredentials
			.builder().Domain(domain).Username(username).Password(password).build())
			.map(this::authorization);
	}

	private Mono<MutableHttpRequest<?>> createPatronAuthRequest(PAPIClient.PatronCredentials patronCredentials) {
		String patronAuthUri = "/PAPIService/REST/public" + URI_PARAMETERS + "/authenticator/patron";
		return createAuthRequest(patronAuthUri, patronCredentials).map(this::authorization);
	}

	private Mono<MutableHttpRequest<?>> createAuthRequest(String path, Object requestBody) {
		return Mono.defer(() -> Mono.just(UriBuilder.of(path).build())
			.map(client::resolve)
			.map(resolvedUri -> HttpRequest.create(POST, resolvedUri.toString()).accept(APPLICATION_JSON))
			.map(request -> request.body(requestBody)));
	}

	private MutableHttpRequest<?> authorization(MutableHttpRequest<?> request) {
		String id = (String) conf.get(ACCESS_ID);
		String key = (String) conf.get(ACCESS_KEY);
		String method = request.getMethod().name();
		String path = request.getUri().toString();
		String date = generateFormattedDate();
		String accessSecret = getAccessSecret(request);

		String signature = calculateApiSignature(key, method, path, date, accessSecret);
		String token = "PWS " + id + ":" + signature;

		return request.header(HttpHeaders.AUTHORIZATION, token).header("PolarisDate", date);
	}

	private String getAccessSecret(MutableHttpRequest<?> request) {
		String path = request.getPath();

		if (overridemethod) {
			//log.debug("overriding public api sig with staff secret: {}", path);
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

	private String calculateApiSignature(String accessKey, String method, String path, String date, String password) {
		try {
			Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
			byte[] secretBytes = accessKey.getBytes();
			SecretKeySpec signingKey = new SecretKeySpec(secretBytes, HMAC_SHA1_ALGORITHM);
			mac.init(signingKey);

			String data = method + path + date + (password != null && !password.isEmpty() ? password : "");
//			log.debug("Data to hash: {}", data);

			byte[] rawHmac = mac.doFinal(data.getBytes());
			return Base64.getEncoder().encodeToString(rawHmac);
		} catch (Exception e) {
			// Handle any exceptions that might occur during the calculation
			throw new RuntimeException("Error calculating PAPI API signature", e);
		}
	}

	private String generateFormattedDate() {
		final var formatter = DateTimeFormatter
			.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z")
			.withZone(ZoneId.of("GMT"))
			.withLocale(Locale.ENGLISH);;
		return formatter.format(Instant.now());
	}

	private static Instant instantOf(String dateStr) {
		Pattern pattern = Pattern.compile("\\/Date\\((\\d+)([+-]\\d+)\\)\\/");
		Matcher matcher = pattern.matcher(dateStr);

		if (matcher.matches()) {
			final var timestamp = Long.parseLong(matcher.group(1));
			final var timeZoneOffsetMinutes = Integer.parseInt(matcher.group(2));
			final var timeZoneOffsetMillis = timeZoneOffsetMinutes * 60 * 1000;
			final var timestampWithOffset = timestamp - timeZoneOffsetMillis;
			return Instant.ofEpochMilli(timestampWithOffset);
		} else {
			throw new IllegalArgumentException("Invalid date string format");
		}
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
		public Boolean isExpired() {
			return instantOf(authExpDate).isBefore(Instant.now());
		}
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
		public Boolean isExpired() {
			return instantOf(authExpDate).isBefore(Instant.now());
		}
	}
}
