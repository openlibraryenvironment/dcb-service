package org.olf.dcb.core.interaction.polaris.papi;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MutableHttpRequest;
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
import static org.olf.dcb.core.interaction.polaris.papi.PAPIConstants.*;

class AuthFilter {
	static final Logger log = LoggerFactory.getLogger(AuthFilter.class);
	private AuthToken currentToken;
	private PatronAuthToken patronAuthToken;
	private final PAPILmsClient client;
	public AuthFilter(PAPILmsClient client) {
		this.client = client;
	}

	public Mono<MutableHttpRequest<?>> ensureStaffAuth(MutableHttpRequest<?> request) {
		return staffAuthentication(request).map(this::authorization);
	}

	public Mono<MutableHttpRequest<?>> ensurePublicAuth(MutableHttpRequest<?> request,
		PAPILmsClient.PatronCredentials patronCredentials) {
		return patronAuthentication(request, patronCredentials).map(this::authorization);
	}

	private <T> Mono<MutableHttpRequest<?>> staffAuthentication (MutableHttpRequest<?> request) {
		return Mono.justOrEmpty(currentToken)
			.filter(token -> !token.isExpired())
			.switchIfEmpty( staffAuthenticator().map(newToken -> currentToken = newToken) )
			.map(validToken -> {
				final String token = validToken.getAccessToken();
				if (request.getPath().contains("protected")) {
					// Update the request path to include the token
					final var General_URI_Parameters = client.getGeneralUriParameters();
					final var currentPath = request.getPath();
					final var target = "/PAPIService/REST/protected" + General_URI_Parameters;
					final var replacement = "/PAPIService/REST/protected" + General_URI_Parameters + "/" + token;
					final var newPath = currentPath.replace(target, replacement);
					// update request with new path
					return request.uri(uriBuilder -> uriBuilder.replacePath(newPath)).header("X-PAPI-AccessToken", token);
				}
				return request.header("X-PAPI-AccessToken", token);
			});
	}

	private Mono<AuthToken> staffAuthenticator() {
		final Map<String, Object> conf = client.getHostLms().getClientConfig();
		final String domain = (String) conf.get(DOMAIN_ID);
		final String username = (String) conf.get(STAFF_USERNAME);
		final String password = (String) conf.get(STAFF_PASSWORD);
		return Mono.just(UriBuilder.of("/PAPIService/REST/protected" + client.getGeneralUriParameters() + "/authenticator/staff").build())
			.map(client::resolve)
			.map(resolvedUri -> HttpRequest.create(POST, resolvedUri.toString()).accept(APPLICATION_JSON))
			.map(request -> request.body(StaffCredentials.builder().Domain(domain).Username(username).Password(password).build()))
			.map(this::authorization)
			.flatMap(request -> client.exchange(request, AuthToken.class))
			.flatMap(response -> Mono.justOrEmpty(response.getBody()));
	}

	private <T> Mono<MutableHttpRequest<?>> patronAuthentication (MutableHttpRequest<?> request,
		PAPILmsClient.PatronCredentials patronCredentials) {
		return Mono.justOrEmpty(patronAuthToken)
			.filter(token -> !token.isExpired())
			.switchIfEmpty( patronAuthenticator(patronCredentials).map(newToken -> patronAuthToken = newToken) )
			.map(token -> request);
	}

	public Mono<PatronAuthToken> patronAuthenticator(PAPILmsClient.PatronCredentials patronCredentials) {
		return Mono.just(UriBuilder.of("/PAPIService/REST/public" + client.getGeneralUriParameters() + "/authenticator/patron").build())
			.map(client::resolve)
			.map(resolvedUri -> HttpRequest.create(POST, resolvedUri.toString()).accept(APPLICATION_JSON))
			.map(request -> request.body(patronCredentials))
			.map(this::authorization)
			.flatMap(request -> client.exchange(request, PatronAuthToken.class))
			.flatMap(response -> Mono.justOrEmpty(response.getBody()));
	}

	private MutableHttpRequest<?> authorization (MutableHttpRequest<?> request) {
		// Calculate the authentication header value
		final Map<String, Object> conf = client.getHostLms().getClientConfig();
		final String id = (String) conf.get(ACCESS_ID);
		final String key = (String) conf.get(ACCESS_KEY);
		final String method = request.getMethod().name();
		final String path = request.getUri().toString();
		final String date = generateFormattedDate();
		String accessSecret = "";

		if (request.getPath().contains("public")) {
			accessSecret = Optional.ofNullable(patronAuthToken)
				.filter(token -> token.getAccessSecret() != null)
				.map(PatronAuthToken::getAccessSecret)
				.orElse("");
		}

		if (request.getPath().contains("protected")) {
			accessSecret = Optional.ofNullable(currentToken)
				.filter(token -> token.getAccessSecret() != null)
				.map(AuthToken::getAccessSecret)
				.orElse("");
		}

		String signature = calculateApiSignature(key, method, path, date, accessSecret);
		final var token = "PWS " + id + ":" + signature;

		request.header(HttpHeaders.AUTHORIZATION, token);
		request.header("PolarisDate", date);

//		log.debug("authorization token: " + token);
		return request;
	}

	private String calculateApiSignature(
		String accessKey, String method, String path, String date, String password) {
//		log.debug("accessKey: {}, method: {}, path: {}, date: {}, password: {}", accessKey, method, path, date, password);
		try {
			Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
			byte[] secretBytes = accessKey.getBytes();
			SecretKeySpec signingKey = new SecretKeySpec(secretBytes, HMAC_SHA1_ALGORITHM);
			mac.init(signingKey);

			String data = method + path + date + (password != null && !password.isEmpty() ? password : "");
//			log.debug("encodeStr: {}", data);

			byte[] rawHmac = mac.doFinal(data.getBytes());
			return Base64.getEncoder().encodeToString(rawHmac);
		} catch (Exception e) {
			// Handle any exceptions that might occur during the calculation
			throw new RuntimeException("Error calculating API signature", e);
		}
	}

	private String generateFormattedDate() {
		final var formatter = DateTimeFormatter
			.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z")
			.withZone(ZoneId.of("GMT"))
			.withLocale(Locale.ENGLISH);;
		return formatter.format(Instant.now());
	}

	static Instant instantOf(String dateStr) {
		Pattern pattern = Pattern.compile("\\/Date\\((\\d+)([+-]\\d+)\\)\\/");
		Matcher matcher = pattern.matcher(dateStr);

		if (matcher.matches()) {
			long timestamp = Long.parseLong(matcher.group(1));
			int timeZoneOffsetMinutes = Integer.parseInt(matcher.group(2));
			int timeZoneOffsetMillis = timeZoneOffsetMinutes * 60 * 1000;
			long timestampWithOffset = timestamp - timeZoneOffsetMillis;
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
		private int papiErrorCode;
		@JsonProperty("ErrorMessage")
		private String errorMessage;
		@JsonProperty("AccessToken")
		private String accessToken;
		@JsonProperty("AccessSecret")
		private String accessSecret;
		@JsonProperty("PatronID")
		private int patronID;
		@JsonProperty("AuthExpDate")
		private String authExpDate;
		public boolean isExpired() {
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
		private int papiErrorCode;

		@JsonProperty("ErrorMessage")
		private String errorMessage;

		@JsonProperty("AccessToken")
		private String accessToken;

		@JsonProperty("AccessSecret")
		private String accessSecret;

		@JsonProperty("PolarisUserID")
		private int polarisUserID;

		@JsonProperty("BranchID")
		private int branchID;
		@JsonProperty("AuthExpDate")
		private String authExpDate;
		public boolean isExpired() {
			return instantOf(authExpDate).isBefore(Instant.now());
		}

	}
}
