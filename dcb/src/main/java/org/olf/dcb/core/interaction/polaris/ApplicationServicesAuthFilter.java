package org.olf.dcb.core.interaction.polaris;

import static io.micronaut.http.HttpHeaders.AUTHORIZATION;
import static io.micronaut.http.HttpMethod.POST;
import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static org.olf.dcb.core.interaction.polaris.PolarisConstants.DOMAIN_ID;
import static org.olf.dcb.core.interaction.polaris.PolarisConstants.SERVICES_LANGUAGE;
import static org.olf.dcb.core.interaction.polaris.PolarisConstants.SERVICES_PRODUCT_ID;
import static org.olf.dcb.core.interaction.polaris.PolarisConstants.SERVICES_SITE_DOMAIN;
import static org.olf.dcb.core.interaction.polaris.PolarisConstants.SERVICES_VERSION;
import static org.olf.dcb.core.interaction.polaris.PolarisConstants.STAFF_PASSWORD;
import static org.olf.dcb.core.interaction.polaris.PolarisConstants.STAFF_USERNAME;

import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import reactor.core.publisher.Mono;

class ApplicationServicesAuthFilter {
	private final PolarisLmsClient client;
	private final Map<String, Object> conf;

	private AuthToken currentToken;

	public ApplicationServicesAuthFilter(PolarisLmsClient client) {
		this.client = client;
		this.conf = client.getConfig();
	}

	Mono<MutableHttpRequest<?>> basicAuth(MutableHttpRequest<?> request) {
		return Mono.justOrEmpty(currentToken)
			.filter(token -> !token.isExpired())
			.switchIfEmpty(staffAuthenticator().map(newToken -> currentToken = newToken))
			.map(validToken -> {
				final var token = validToken.getAccessToken();
				final var secret = validToken.getAccessSecret();

				if (!request.getPath().contains("/authentication/staffuser")) {
					// update request
					final var servicesConfig = client.getServicesConfig();
					final var domain = (String) servicesConfig.get(SERVICES_SITE_DOMAIN);
					final var authorization = "PAS " + domain + ":" + token + ":" + secret;

					return request.header(AUTHORIZATION, authorization);
				}

				return request;
			});
	}

	private Mono<AuthToken> staffAuthenticator() {
		final var servicesMap = client.getServicesConfig();

		final String version = (String) servicesMap.get(SERVICES_VERSION);
		final String language = (String) servicesMap.get(SERVICES_LANGUAGE);
		final String product = (String) servicesMap.get(SERVICES_PRODUCT_ID);
		final String parameters = "/polaris.applicationservices/api/" + version + "/" + language + "/" + product;

		return Mono.just(UriBuilder.of(parameters + "/authentication/staffuser").build())
			.map(client::resolve)
			.map(resolvedUri -> HttpRequest.create(POST, resolvedUri.toString()).accept(APPLICATION_JSON))
			.map(this::staffAuthorization)
			// empty body needs to be passed to make successful call
			.map(request -> request.body(""))
			.flatMap(request -> client.exchange(request, AuthToken.class, Boolean.TRUE))
			.flatMap(response -> Mono.justOrEmpty(response.getBody()));
	}

	private MutableHttpRequest<?> staffAuthorization (MutableHttpRequest<?> request) {
		// Calculate the authentication header value
		final var id = (String) conf.get(DOMAIN_ID);
		final var username = (String) conf.get(STAFF_USERNAME);
		final var password = (String) conf.get(STAFF_PASSWORD);

		final var string = id + "\\" + username + ":" + password;
		final var signature = Base64.getEncoder().encodeToString(string.getBytes());
		final var token = "Basic " + signature;

		return request.header(AUTHORIZATION, token);
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	private static class AuthToken {
		@JsonProperty("SiteDomain")
		private String siteDomain;
		@JsonProperty("UserDomain")
		private String userDomain;
		@JsonProperty("AccessToken")
		private String accessToken;
		@JsonProperty("AccessSecret")
		private String accessSecret;
		@JsonProperty("AuthExpDate")
		private String authExpDate;
		@JsonProperty("PolarisUser")
		private PolarisUser polarisUser;
		@JsonProperty("ERMSNetworkAddress")
		private String ermsNetworkAddress;
		@JsonProperty("DataSource")
		private String dataSource;

		public boolean isExpired() {
			Instant authExpInstant = Instant.parse(authExpDate);
			return authExpInstant.isBefore(Instant.now());
		}
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	private static class PolarisUser {
		@JsonProperty("PolarisUserID")
		private Integer polarisUserID;
		@JsonProperty("OrganizationID")
		private Integer organizationID;
		@JsonProperty("Name")
		private String name;
		@JsonProperty("BranchID")
		private Integer branchID;
		@JsonProperty("Enabled")
		private Boolean enabled;
		@JsonProperty("CreatorID")
		private Integer creatorID;
		@JsonProperty("ModifierID")
		private Integer modifierID;
		@JsonProperty("CreationDate")
		private String creationDate;
		@JsonProperty("ModificationDate")
		private String modificationDate;
	}
}
