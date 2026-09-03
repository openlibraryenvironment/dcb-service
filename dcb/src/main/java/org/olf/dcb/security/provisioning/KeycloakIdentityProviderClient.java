package org.olf.dcb.security.provisioning;

import static io.micronaut.http.MediaType.APPLICATION_FORM_URLENCODED;
import static io.micronaut.http.MediaType.APPLICATION_JSON;

import java.net.URI;
import java.util.Arrays;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Provisioning against Keycloak's admin REST API.
 *
 * <p><b>Containment.</b> The service account holds {@code view-users} and {@code query-users}
 * but deliberately NOT {@code manage-users}: measured on a live realm, {@code manage-users}
 * can map any realm role including {@code ADMIN}, and fine-grained permissions do not
 * constrain it. Create and modify power comes from fine-grained user permissions instead,
 * with {@code map-role} attached to {@code LIBRARY_ADMIN} and {@code LIBRARY_READ_ONLY} only.
 * Verify this against the deployed Keycloak version — fine-grained admin permissions have
 * moved between releases, and an unverified assumption here reads as a control while being
 * none.
 *
 * <p>Setup, the grant, and the commands that prove it:
 * {@code docs/identity-provider-setup.md} §2.2–2.3 and §5.3;
 * {@code scripts/keycloak_library_accounts_setup.sh} configures and asserts it.
 */
@Singleton
@Requires(property = "dcb.identity-provider.type", value = "keycloak")
public class KeycloakIdentityProviderClient implements IdentityProviderClient {

	private static final Logger log = LoggerFactory.getLogger(KeycloakIdentityProviderClient.class);

	/** What a newly provisioned person is asked to do before they can sign in. */
	private static final List<String> REQUIRED_ACTIONS = List.of("UPDATE_PASSWORD", "VERIFY_EMAIL");

	private final HttpClient httpClient;
	private final IdentityProviderConfig config;
	private final ClientCredentialsTokenSource tokens;

	public KeycloakIdentityProviderClient(@Client HttpClient httpClient,
		IdentityProviderConfig config) {

		this.httpClient = httpClient;
		this.config = config;

		// Checked HERE rather than left to bean validation. A secret that binds to the empty
		// string produces an application that starts, offers account provisioning, and then
		// refuses every account anybody tries to create - with a 401 from Keycloak, weeks
		// later, in an environment somebody has already been told is working. Failing at
		// construction makes the misconfiguration arrive at deploy time, addressed to the
		// person who caused it.
		final var clientSecret = config.getClientSecret();

		if (clientSecret == null || clientSecret.isBlank()) {
			throw new IllegalStateException(
				"dcb.identity-provider.client-secret is required when the type is keycloak, and "
					+ "has no default");
		}

		this.tokens = new ClientCredentialsTokenSource(httpClient, tokenUri(config),
			config.getClientId().orElseThrow(() -> new IllegalStateException(
				"dcb.identity-provider.client-id is required when the type is keycloak")),
			clientSecret);
	}

	@Override
	public String providerName() {
		return "keycloak";
	}

	@Override
	public Mono<ProvisionedUser> provision(ProvisionRequest request) {
		return createDisabled(request)
			.flatMap(userId -> grantRole(userId, request.role())
				.then(enable(userId))
				// Clean up your trash: an account we could not finish CONFIGURING is not left
				// behind for somebody to find and wonder about. Best effort by necessity -
				// this runs on the path that is already failing - so the original error is
				// what propagates, never the compensation's.
				.onErrorResume(error -> deleteQuietly(userId).then(Mono.error(error)))
				// THE INVITATION IS OUTSIDE THAT. A correctly created, correctly scoped
				// account must survive a mail failure: SMTP being down or unconfigured is
				// transient and somebody else's, and the invitation is re-sendable from the
				// accounts page. Deleting the account would turn a mail outage into lost
				// work, and the person would have to be created again by hand.
				//
				// Found by standing the stack up: a local Keycloak with no sender address
				// returns 500 here, and the account was being rolled back every time.
				.then(sendInvite(userId)
					.doOnError(error -> log.error(
						"Account {} was created but its invitation email could not be sent. It "
							+ "is enabled and has its role; resend the invitation once mail works.",
						userId, error))
					.onErrorResume(error -> Mono.empty()))
				.then(readUser(userId)));
	}

	@Override
	public Mono<Void> setEnabled(String providerUserId, boolean enabled) {
		return authorised(HttpRequest.PUT(userUri(providerUserId), Map.of("enabled", enabled)))
			.flatMap(request -> Mono.from(httpClient.exchange(request, Void.class)))
			.then();
	}

	@Override
	public Mono<Void> sendInvite(String providerUserId) {
		final var uri = UriBuilder.of(userUri(providerUserId)).path("execute-actions-email").build();

		return authorised(HttpRequest.PUT(uri, REQUIRED_ACTIONS))
			.flatMap(request -> Mono.from(httpClient.exchange(request, Void.class)))
			.then();
	}

	@Override
	public Flux<ProvisionedUser> findByIds(Iterable<String> providerUserIds) {
		// One request per account, sequentially. The caller is one library's staff list -
		// tens of rows - and Keycloak is a third-party system we do not own, so this fans
		// out at a concurrency of one rather than however many rows happen to exist.
		return Flux.fromIterable(providerUserIds)
			.concatMap(id -> readUser(id)
				.onErrorResume(error -> {
					// A user deleted at the provider is a real state, not a failure of the
					// listing. Log it and leave the row rendering its stored status.
					log.warn("Could not read Keycloak user {}: {}", id, error.getMessage());
					return Mono.empty();
				}));
	}

	private Mono<String> createDisabled(ProvisionRequest request) {
		final var body = Map.of(
			"username", request.email(),
			"email", request.email(),
			"firstName", request.firstName() == null ? "" : request.firstName(),
			"lastName", request.lastName() == null ? "" : request.lastName(),
			"enabled", false,
			"emailVerified", false,
			// The claim AgencyClaims reads.
			"attributes", Map.of("code", List.of(request.agencyCode())));

		return describing("POST /users", authorised(HttpRequest.POST(usersUri(), body))
				.flatMap(post -> Mono.from(httpClient.exchange(post, Void.class)))
				.map(KeycloakIdentityProviderClient::userIdFromLocation))
			// Keycloak's usernames are unique across the realm, so provisioning somebody who
			// already has an account AT ANOTHER LIBRARY fails here rather than in
			// refuseDuplicate, which only knows about this library. Untranslated it reaches
			// the user as a bare client exception, while the same-library case gets a worded
			// conflict - the same mistake reported two different ways.
			.onErrorMap(HttpClientResponseException.class,
				failure -> failure.getStatus() == HttpStatus.CONFLICT
					? new HttpStatusException(HttpStatus.CONFLICT,
						"The identity provider already has an account for that email address, "
							+ "possibly at another library. DCB creates one provider account per "
							+ "person and cannot yet attach a second library to one.")
					: failure);
	}

	/**
	 * Read the role representation, then map it. Keycloak's role-mapping endpoint takes the
	 * full representation rather than a name, so the read is required rather than decorative,
	 * and it fails closed if the role is not there - the right outcome for an unprepared realm.
	 *
	 * <p><b>Read from the USER's available roles, not the realm's role catalogue.</b>
	 * {@code GET /roles/{name}} needs {@code view-realm}, which the documented service account
	 * deliberately does not hold - so the obvious call returns 403 against a correctly
	 * least-privileged realm. This endpoint is covered by {@code manage-users}, and it asks a
	 * better question anyway: what may this user be granted, rather than what roles exist.
	 *
	 * <p>Found by standing the stack up against a real Keycloak. The implementation and the
	 * runbook disagreed, and the runbook was right.
	 */
	private Mono<Void> grantRole(String userId, ProvisionableRole role) {
		final var availableUri = UriBuilder.of(userUri(userId))
			.path("role-mappings").path("realm").path("available").build();

		final var mappingUri = UriBuilder.of(userUri(userId))
			.path("role-mappings").path("realm").build();

		return describing("GET role-mappings/realm/available",
				authorised(HttpRequest.GET(availableUri))
					.flatMap(get -> Mono.from(httpClient.retrieve(get, KeycloakRole[].class))))
			.flatMap(available -> Arrays.stream(available)
				.filter(candidate -> role.name().equals(candidate.name()))
				.findFirst()
				.map(Mono::just)
				.orElseGet(() -> Mono.error(new IllegalStateException(
					"Role " + role.name() + " is not available to grant. Either the realm does not "
						+ "have it, or this service account may not map it."))))
			.flatMap(representation -> describing("POST role-mappings/realm",
				authorised(HttpRequest.POST(mappingUri, List.of(representation)))
					.flatMap(post -> Mono.from(httpClient.exchange(post, Void.class)))))
			.then();
	}

	private Mono<Void> enable(String userId) {
		return setEnabled(userId, true);
	}

	private Mono<ProvisionedUser> readUser(String userId) {
		return authorised(HttpRequest.GET(userUri(userId)))
			.flatMap(get -> Mono.from(httpClient.retrieve(get, KeycloakUser.class)))
			.map(user -> new ProvisionedUser(userId, user.email(), user.firstName(),
				user.lastName(), Boolean.TRUE.equals(user.enabled()),
				Boolean.TRUE.equals(user.emailVerified())));
	}

	private Mono<Void> deleteQuietly(String userId) {
		return authorised(HttpRequest.DELETE(userUri(userId)))
			.flatMap(delete -> Mono.from(httpClient.exchange(delete, Void.class)))
			.doOnError(error -> log.error(
				"Could not remove the half-provisioned Keycloak user {}. It is disabled and "
					+ "roleless, so it confers nothing, but it should be removed by hand.",
				userId, error))
			.onErrorResume(error -> Mono.empty())
			.then();
	}

	private <T> Mono<io.micronaut.http.MutableHttpRequest<T>> authorised(
		io.micronaut.http.MutableHttpRequest<T> request) {

		return tokens.get().map(token -> request
			.bearerAuth(token)
			.contentType(APPLICATION_JSON)
			.accept(APPLICATION_JSON));
	}

	/**
	 * Say WHICH call failed and what the provider said about it.
	 *
	 * <p>Without this the whole flow surfaces as the single word "Forbidden" - the reason
	 * phrase of whichever of six admin calls happened to fail - and diagnosing it means
	 * reproducing each call by hand against the same realm. A refusal nobody can attribute
	 * to a call is a refusal nobody can investigate.
	 *
	 * <p>Keycloak's admin error bodies name the missing permission and carry no credential.
	 * The one place that is NOT true is the token endpoint, whose request echoes the client
	 * secret - which is why {@code fetch()} logs only an exception type and never goes
	 * through here.
	 */
	private <T> Mono<T> describing(String call, Mono<T> operation) {
		return operation.doOnError(HttpClientResponseException.class, failure -> log.error(
			"Keycloak refused {}: {} {} - {}", call, failure.getStatus().getCode(),
			failure.getStatus().getReason(),
			failure.getResponse().getBody(String.class).orElse("<no body>")));
	}

	/**
	 * Keycloak returns the new user's id only in the {@code Location} header. A response
	 * without one means the account may or may not have been created, so this fails rather
	 * than inventing an id to carry on with.
	 */
	private static String userIdFromLocation(HttpResponse<?> response) {
		final var location = response.header("Location");

		if (location == null || location.isBlank()) {
			throw new IllegalStateException(
				"Keycloak created a user but returned no Location header, so its id is unknown");
		}

		return location.substring(location.lastIndexOf('/') + 1);
	}

	private URI realmUri() {
		return UriBuilder.of(URI.create(config.getBaseUrl().orElseThrow(
				() -> new IllegalStateException("dcb.identity-provider.base-url is required"))))
			.path("admin").path("realms").path(realm()).build();
	}

	private URI usersUri() {
		return UriBuilder.of(realmUri()).path("users").build();
	}

	private URI userUri(String userId) {
		return UriBuilder.of(usersUri()).path(userId).build();
	}

	private String realm() {
		return config.getRealm().orElseThrow(
			() -> new IllegalStateException("dcb.identity-provider.realm is required for keycloak"));
	}

	private static URI tokenUri(IdentityProviderConfig config) {
		return UriBuilder.of(URI.create(config.getBaseUrl().orElseThrow(
				() -> new IllegalStateException("dcb.identity-provider.base-url is required"))))
			.path("realms")
			.path(config.getRealm().orElseThrow(
				() -> new IllegalStateException("dcb.identity-provider.realm is required for keycloak")))
			.path("protocol").path("openid-connect").path("token").build();
	}

	@Serdeable
	record KeycloakRole(String id, String name) {
	}

	@Serdeable
	record KeycloakUser(String id, String email, String firstName, String lastName,
		Boolean enabled, Boolean emailVerified) {
	}

	/**
	 * One access token, refreshed a minute before it expires.
	 *
	 * <p>Deliberately a single cached {@link Mono} rather than a map: there is exactly one
	 * service account, and a map keyed on anything here would be a cache with one entry and
	 * room for somebody to add a second key without thinking about its bound.
	 */
	static final class ClientCredentialsTokenSource {
		private final HttpClient httpClient;
		private final URI tokenUri;
		private final String clientId;
		private final String clientSecret;

		private volatile Mono<String> current;

		ClientCredentialsTokenSource(HttpClient httpClient, URI tokenUri, String clientId,
			String clientSecret) {

			this.httpClient = httpClient;
			this.tokenUri = tokenUri;
			this.clientId = clientId;
			this.clientSecret = clientSecret;
		}

		Mono<String> get() {
			// Read once into a local: the field can be replaced between the null check and
			// the return, and a caller receiving a half-built value would be a genuinely
			// horrible bug to find.
			final var cached = current;

			if (cached != null) {
				return cached;
			}

			final var fetched = fetch().cache(
				response -> Duration.ofSeconds(Math.max(response.expiresIn() - 60, 30)),
				error -> Duration.ZERO,
				() -> Duration.ZERO)
				.map(TokenResponse::accessToken);

			current = fetched;

			return fetched;
		}

		private Mono<TokenResponse> fetch() {
			final var form = Map.of(
				"grant_type", "client_credentials",
				"client_id", clientId,
				"client_secret", clientSecret);

			final var request = HttpRequest.POST(tokenUri, form)
				.contentType(APPLICATION_FORM_URLENCODED)
				.accept(APPLICATION_JSON);

			return Mono.from(httpClient.retrieve(request, TokenResponse.class))
				// The message must never carry the response body: a failed token exchange
				// can echo the request, and the request contains the client secret.
				.doOnError(error -> log.error(
					"Could not obtain an identity provider access token: {}",
					error.getClass().getSimpleName()));
		}
	}

	@Serdeable
	record TokenResponse(
		@com.fasterxml.jackson.annotation.JsonProperty("access_token") String accessToken,
		@com.fasterxml.jackson.annotation.JsonProperty("expires_in") long expiresIn) {
	}

	/** Visible for the fetchers' "is provisioning configured" check. */
	@Nullable
	public String projectId() {
		return config.getProjectId().orElse(null);
	}
}
