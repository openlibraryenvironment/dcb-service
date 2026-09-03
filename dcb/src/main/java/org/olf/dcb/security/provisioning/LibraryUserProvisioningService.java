package org.olf.dcb.security.provisioning;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.olf.dcb.core.Constants.UUIDs.NAMESPACE_LIBRARY_USER_ACCOUNTS;

import org.olf.dcb.core.model.Library;
import org.olf.dcb.core.model.LibraryUserAccount;
import org.olf.dcb.storage.LibraryRepository;
import org.olf.dcb.storage.LibraryUserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import services.k_int.utils.UUIDUtils;

/**
 * Creating, listing and disabling DCB Admin for Libraries accounts.
 *
 * <h2>The agency is derived, never supplied</h2>
 *
 * A caller names a library; the agency code comes from that library's record. There is no
 * input field a client could use to scope an account to somebody else's library, which is the
 * same rule as everywhere else here - identity and scope come from data the server resolved,
 * not from the request body.
 *
 * <h2>Provider first, database second</h2>
 *
 * The provider write is the one that cannot be rolled back by a transaction, so it happens
 * first and the local row records what actually exists. The reverse order would leave a
 * binding row pointing at an account that was never created - and a listing would then show
 * somebody an account they do not have.
 *
 * <h2>Scale</h2>
 *
 * Every path here is bounded by one library's staff: tens of rows, one provider call each,
 * issued sequentially. Nothing iterates libraries, and there is no consortium-wide listing to
 * iterate them for.
 */
@Singleton
public class LibraryUserProvisioningService {

	private static final Logger log = LoggerFactory.getLogger(LibraryUserProvisioningService.class);

	private final Optional<IdentityProviderClient> identityProvider;
	private final LibraryUserAccountRepository accountRepository;
	private final LibraryRepository libraryRepository;

	public LibraryUserProvisioningService(Optional<IdentityProviderClient> identityProvider,
		LibraryUserAccountRepository accountRepository, LibraryRepository libraryRepository) {

		this.identityProvider = identityProvider;
		this.accountRepository = accountRepository;
		this.libraryRepository = libraryRepository;
	}

	/**
	 * Whether this deployment can provision at all.
	 *
	 * <p>A deployment with no provider configured has no client bean, and saying so plainly
	 * is better than a 500 from a null. The UI reads the same fact to decide whether to
	 * render the tab.
	 */
	public boolean isConfigured() {
		return identityProvider.isPresent();
	}

	public Mono<LibraryUserAccount> provision(UUID libraryId, String email, String firstName,
		String lastName, ProvisionableRole role, String actor, String reason,
		String changeCategory, String changeReferenceUrl) {

		final var provider = require();

		return library(libraryId)
			.flatMap(library -> {
				final var agencyCode = library.getAgencyCode();

				if (agencyCode == null || agencyCode.isBlank()) {
					// An account with no agency claim sees nothing - empty means "no
					// access", not "no restriction". Refusing here is far kinder than
					// creating an account that signs in to an empty application.
					return Mono.error(new HttpStatusException(HttpStatus.CONFLICT,
						"This library has no agency code, so an account for it would have no "
							+ "access to any data. Set the library's agency first."));
				}

				return refuseDuplicate(libraryId, email)
					.then(provider.provision(new IdentityProviderClient.ProvisionRequest(
						email, firstName, lastName, role, agencyCode)))
					.flatMap(provisioned -> record(provisioned, provider, library, role, actor,
						reason, changeCategory, changeReferenceUrl));
			});
	}

	/**
	 * One library's accounts, with the provider's live view of each folded in.
	 *
	 * <p>The stored status is what renders if the provider is unreachable - a staff list
	 * that disappears when the identity provider hiccups is worse than one that is briefly
	 * out of date and says which library it belongs to.
	 */
	public Flux<LibraryUserAccount> list(UUID libraryId) {
		return Flux.from(accountRepository.findByLibraryIdOrderByEmail(libraryId))
			.collectList()
			.flatMapMany(stored -> {
				if (stored.isEmpty() || identityProvider.isEmpty()) {
					return Flux.fromIterable(stored);
				}

				final var ids = stored.stream()
					.map(LibraryUserAccount::getIdentityProviderUserId)
					.toList();

				return identityProvider.get().findByIds(ids)
					.collectMap(IdentityProviderClient.ProvisionedUser::providerUserId)
					.flatMapMany(live -> Flux.fromIterable(stored)
						.map(account -> reconcile(account, live.get(account.getIdentityProviderUserId()))))
					.onErrorResume(error -> {
						log.warn("Could not read live account state for library {}: {}",
							libraryId, error.getMessage());

						return Flux.fromIterable(stored);
					});
			});
	}

	public Mono<LibraryUserAccount> setEnabled(UUID accountId, boolean enabled, String actor,
		String reason, String changeCategory, String changeReferenceUrl) {

		final var provider = require();

		return account(accountId)
			.flatMap(account -> provider.setEnabled(account.getIdentityProviderUserId(), enabled)
				.then(statusAfter(provider, account, enabled))
				.flatMap(status -> Mono.from(accountRepository.update(account
					.setStatus(status)
					.setLastEditedBy(actor)
					.setReason(reason)
					.setChangeCategory(changeCategory)
					.setChangeReferenceUrl(changeReferenceUrl)))))
			.map(LibraryUserAccount.class::cast);
	}

	/**
	 * Disabling is certain; enabling is not. An enabled account is only ACTIVE once the
	 * person has completed the actions email, which the provider knows and this service does
	 * not — so the enable path reads it back instead of writing ACTIVE and being corrected
	 * by the next listing.
	 */
	private static Mono<LibraryUserStatus> statusAfter(IdentityProviderClient provider,
		LibraryUserAccount account, boolean enabled) {

		if (!enabled) {
			return Mono.just(LibraryUserStatus.DISABLED);
		}

		return provider.findByIds(List.of(account.getIdentityProviderUserId()))
			.next()
			.map(IdentityProviderClient.ProvisionedUser::status)
			.onErrorResume(error -> {
				log.warn("Enabled account {} but could not read its state back: {}",
					account.getId(), error.getMessage());

				return Mono.empty();
			})
			// The weaker claim when we cannot tell: an account shown as invited that is
			// really active corrects itself on the next listing, whereas one shown as active
			// that has never had a password sends somebody to chase a working login.
			.defaultIfEmpty(LibraryUserStatus.INVITED);
	}

	public Mono<LibraryUserAccount> resendInvite(UUID accountId) {
		final var provider = require();

		return account(accountId)
			.flatMap(account -> provider.sendInvite(account.getIdentityProviderUserId())
				.thenReturn(account));
	}

	private Mono<LibraryUserAccount> record(IdentityProviderClient.ProvisionedUser provisioned,
		IdentityProviderClient provider, Library library, ProvisionableRole role, String actor,
		String reason, String changeCategory, String changeReferenceUrl) {

		final var account = LibraryUserAccount.builder()
			// Derived from the provider's id so a retry of the same provision cannot make
			// a second binding for one account. The unique constraint would catch it; a
			// deterministic id means it never gets that far.
			.id(UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_LIBRARY_USER_ACCOUNTS,
				provider.providerName() + ":" + provisioned.providerUserId()))
			.identityProvider(provider.providerName())
			.identityProviderUserId(provisioned.providerUserId())
			.libraryId(library.getId())
			.agencyCode(library.getAgencyCode())
			.email(provisioned.email())
			.firstName(provisioned.firstName())
			.lastName(provisioned.lastName())
			.role(role)
			.status(provisioned.status())
			.lastEditedBy(actor)
			.reason(reason)
			.changeCategory(changeCategory)
			.changeReferenceUrl(changeReferenceUrl)
			.build();

		return Mono.from(accountRepository.save(account))
			.map(LibraryUserAccount.class::cast);
	}

	/**
	 * The stored row updated with what the provider actually says, without writing back.
	 *
	 * <p>Not persisted: the provider owns this fact, and a read that writes turns every
	 * listing into a write amplification against the database for no gain.
	 */
	private static LibraryUserAccount reconcile(LibraryUserAccount account,
		IdentityProviderClient.ProvisionedUser live) {

		return live == null ? account : account.setStatus(live.status());
	}

	private Mono<Void> refuseDuplicate(UUID libraryId, String email) {
		return Mono.from(accountRepository.findByLibraryIdAndEmail(libraryId, email))
			.flatMap(existing -> Mono.<Void>error(new HttpStatusException(HttpStatus.CONFLICT,
				"This library already has an account for that email address.")))
			.then();
	}

	private Mono<Library> library(UUID libraryId) {
		return Mono.from(libraryRepository.findById(libraryId))
			.switchIfEmpty(Mono.error(new HttpStatusException(HttpStatus.NOT_FOUND,
				"No library with that id.")))
			.map(Library.class::cast);
	}

	private Mono<LibraryUserAccount> account(UUID accountId) {
		return Mono.from(accountRepository.findById(accountId))
			.switchIfEmpty(Mono.error(new HttpStatusException(HttpStatus.NOT_FOUND,
				"No account with that id.")));
	}

	private IdentityProviderClient require() {
		return identityProvider.orElseThrow(() -> new HttpStatusException(
			HttpStatus.SERVICE_UNAVAILABLE,
			"Account provisioning is not configured on this deployment."));
	}

}
