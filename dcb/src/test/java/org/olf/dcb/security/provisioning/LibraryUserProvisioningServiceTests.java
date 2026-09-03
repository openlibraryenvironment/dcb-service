package org.olf.dcb.security.provisioning;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.LibraryUserAccount;
import org.olf.dcb.storage.LibraryRepository;
import org.olf.dcb.storage.LibraryUserAccountRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The identity provider owns whether an account is active, and this service must not
 * overwrite that with what the caller asked for.
 *
 * Enabling an account is not the same as it becoming usable: the person still has to
 * complete the actions email. Deriving the status from the request wrote ACTIVE over an
 * INVITED account, returned it to the accounts grid, and was then silently contradicted by
 * the next listing, which reconciles against the provider.
 */
class LibraryUserProvisioningServiceTests {

	private static final UUID ACCOUNT_ID = UUID.randomUUID();
	private static final String PROVIDER_USER_ID = "keycloak-user-1";

	private IdentityProviderClient provider;
	private LibraryUserAccountRepository accounts;
	private LibraryUserProvisioningService service;

	@BeforeEach
	void setUp() {
		provider = mock(IdentityProviderClient.class);
		accounts = mock(LibraryUserAccountRepository.class);

		service = new LibraryUserProvisioningService(Optional.of(provider), accounts,
			mock(LibraryRepository.class));

		when(accounts.findById(ACCOUNT_ID)).thenReturn(Mono.just(storedAccount()));
		when(accounts.update(any())).thenAnswer(call -> Mono.just(call.getArgument(0)));
		when(provider.setEnabled(anyString(), anyBoolean())).thenReturn(Mono.empty());
	}

	@Test
	@DisplayName("Enabling an account nobody has verified leaves it INVITED")
	void enablingAnUnverifiedAccountDoesNotClaimItIsActive() {
		providerReports(true, false);

		assertThat(setEnabled(true).getStatus(), is(LibraryUserStatus.INVITED));
	}

	@Test
	@DisplayName("Enabling an account that has been verified makes it ACTIVE")
	void enablingAVerifiedAccountIsActive() {
		providerReports(true, true);

		assertThat(setEnabled(true).getStatus(), is(LibraryUserStatus.ACTIVE));
	}

	@Test
	@DisplayName("An unreadable provider leaves the weaker claim")
	void anUnreadableProviderDoesNotClaimActive() {
		when(provider.findByIds(any())).thenReturn(Flux.error(new RuntimeException("unreachable")));

		assertThat(setEnabled(true).getStatus(), is(LibraryUserStatus.INVITED));
	}

	@Test
	@DisplayName("Disabling is certain, and asks the provider nothing")
	void disablingDoesNotNeedToAsk() {
		// The one case the caller genuinely knows the answer to. Reading back would also let
		// a stale provider view report an account we have just disabled as ACTIVE.
		assertThat(setEnabled(false).getStatus(), is(LibraryUserStatus.DISABLED));

		verify(provider, never()).findByIds(any());
	}

	private void providerReports(boolean enabled, boolean emailVerified) {
		when(provider.findByIds(any())).thenReturn(Flux.just(
			new IdentityProviderClient.ProvisionedUser(PROVIDER_USER_ID, "someone@library.test",
				null, null, enabled, emailVerified)));
	}

	private LibraryUserAccount setEnabled(boolean enabled) {
		return service.setEnabled(ACCOUNT_ID, enabled, "a-consortium-admin", null, null, null)
			.block();
	}

	private static LibraryUserAccount storedAccount() {
		return LibraryUserAccount.builder()
			.id(ACCOUNT_ID)
			.identityProvider("keycloak")
			.identityProviderUserId(PROVIDER_USER_ID)
			.libraryId(UUID.randomUUID())
			.agencyCode("AGENCY-1")
			.email("someone@library.test")
			.role(ProvisionableRole.LIBRARY_READ_ONLY)
			.status(LibraryUserStatus.INVITED)
			.build();
	}
}
