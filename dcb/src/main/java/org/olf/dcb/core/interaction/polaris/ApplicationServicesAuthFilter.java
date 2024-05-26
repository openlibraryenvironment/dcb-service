package org.olf.dcb.core.interaction.polaris;

import static io.micronaut.http.HttpHeaders.AUTHORIZATION;
import static io.micronaut.http.HttpMethod.POST;
import static io.micronaut.http.MediaType.APPLICATION_JSON;

import java.time.Instant;
import java.util.Base64;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
class ApplicationServicesAuthFilter {
	private final PolarisLmsClient client;
	private final PolarisConfig polarisConfig;

	private AuthToken currentToken;

	public ApplicationServicesAuthFilter(PolarisLmsClient client, PolarisConfig polarisConfig) {
		this.client = client;
		this.polarisConfig = polarisConfig;
	}

	Mono<MutableHttpRequest<?>> basicAuth(MutableHttpRequest<?> request) {
		return staffAuthenticator()
			.map(newToken -> currentToken = newToken)
			.map(validToken -> {
				final var token = validToken.getAccessToken();
				final var secret = validToken.getAccessSecret();

				if (!request.getPath().contains("/authentication/staffuser")) {
					// update request
					final var domain = polarisConfig.getServicesSiteDomain();
					final var authorization = "PAS " + domain + ":" + token + ":" + secret;

					return request.header(AUTHORIZATION, authorization);
				}

				return request;
			}).doOnSuccess(req -> log.debug("Request '{}' completed staff auth.", req.getPath()));
	}

	private Mono<AuthToken> staffAuthenticator() {
		return Mono.just(UriBuilder.of( staffAuthenticatorPath() ).build())
			.map(uri -> client.isApplicationServicesBaseUrlPresent()
				? client.overrideResolve(uri)
				: client.defaultResolve(uri))
			.map(resolvedUri -> HttpRequest.create(POST, resolvedUri.toString()).accept(APPLICATION_JSON))
			.map(this::staffAuthorization)
			// empty body needs to be passed to make successful call
			.map(request -> request.body(""))
			.flatMap(request -> client.exchange(request, AuthToken.class, Boolean.TRUE))
			.flatMap(response -> Mono.justOrEmpty(response.getBody()));
	}

	private String staffAuthenticatorPath() {
		final String version = polarisConfig.getServicesVersion();
		final String language = polarisConfig.getServicesLanguage();
		final String product = polarisConfig.getServicesProductId();
		final String parameters = "/polaris.applicationservices/api/" + version + "/" + language + "/" + product;
		return parameters + "/authentication/staffuser";
	}

	private MutableHttpRequest<?> staffAuthorization (MutableHttpRequest<?> request) {
		// Calculate the authentication header value
		final var id = polarisConfig.getDomainId();
		final var username = polarisConfig.getStaffUsername();
		final var password = polarisConfig.getStaffPassword();

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
