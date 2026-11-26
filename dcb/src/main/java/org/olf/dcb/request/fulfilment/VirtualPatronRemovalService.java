package org.olf.dcb.request.fulfilment;

import static io.micronaut.http.HttpStatus.CONFLICT;
import static io.micronaut.http.HttpStatus.FORBIDDEN;
import static io.micronaut.http.HttpStatus.NOT_FOUND;
import static io.micronaut.http.HttpStatus.TOO_MANY_REQUESTS;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.MultipleVirtualPatronsFound;
import org.olf.dcb.core.interaction.NotDcbsVirtualPatronException;
import org.olf.dcb.core.interaction.VirtualPatronNotFound;
import org.olf.dcb.core.interaction.VirtualPatronRemoval;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Event;
import org.olf.dcb.core.model.EventType;
import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.request.fulfilment.PatronService.PatronId;
import org.olf.dcb.security.CallerScope;
import org.olf.dcb.storage.AgencyRepository;
import org.olf.dcb.storage.EventLogRepository;
import org.olf.dcb.storage.PatronIdentityRepository;
import org.olf.dcb.storage.PatronRequestRepository;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Staff remove a virtual patron DCB created in a library's system: deleted where that system can
 * delete one, expired where it can only expire one. DCB never does this on its own. A home identity,
 * a borrower with any request not yet finalised, and a record the library's system does not confirm
 * as DCB's virtual patron for that borrower are all refused.
 */
@Slf4j
@Singleton
public class VirtualPatronRemovalService {
	private final PatronService patronService;
	private final PatronIdentityRepository patronIdentityRepository;
	private final PatronRequestRepository patronRequestRepository;
	private final AgencyRepository agencyRepository;
	private final HostLmsService hostLmsService;
	private final VirtualIdentityRecords virtualIdentityRecords;
	private final EventLogRepository eventLogRepository;
	private final VirtualPatronRemovalLimiter limiter;

	public VirtualPatronRemovalService(PatronService patronService,
		PatronIdentityRepository patronIdentityRepository, PatronRequestRepository patronRequestRepository,
		AgencyRepository agencyRepository, HostLmsService hostLmsService,
		VirtualIdentityRecords virtualIdentityRecords, EventLogRepository eventLogRepository,
		VirtualPatronRemovalLimiter limiter) {

		this.patronService = patronService;
		this.patronIdentityRepository = patronIdentityRepository;
		this.patronRequestRepository = patronRequestRepository;
		this.agencyRepository = agencyRepository;
		this.hostLmsService = hostLmsService;
		this.virtualIdentityRecords = virtualIdentityRecords;
		this.eventLogRepository = eventLogRepository;
		this.limiter = limiter;
	}

	/** One virtual patron. A library caller must be the borrower's library or the library whose system holds it. */
	public Mono<VirtualPatronRemovalResult> removeOne(UUID identityId, @Nullable VirtualPatronRemoval requested,
		CallerScope caller, String callerSubject) {

		if (!limiter.tryAcquireForCaller(callerSubject)) {
			return Mono.error(tooMany());
		}

		return patronService.getPatronIdentityById(identityId)
			.switchIfEmpty(Mono.error(() -> notFound(caller, "No patron identity has this id.")))
			.flatMap(identity -> patronService.findById(PatronId.fromIdentity(identity))
				.switchIfEmpty(Mono.error(() -> notFound(caller, "This identity's patron no longer exists.")))
				.flatMap(patron -> ensureAllowed(caller, patron, identity.getHostLms(), true).thenReturn(patron))
				.flatMap(patron -> ensureVirtual(identity).thenReturn(patron))
				.flatMap(patron -> ensureNoUnfinishedRequests(patron).thenReturn(patron))
				.flatMap(patron -> remove(identity, patron, requested, callerSubject)));
	}

	/** Every virtual patron of one borrower, one library at a time. A library caller must be the borrower's library. */
	public Mono<List<VirtualPatronRemovalResult>> removeAll(UUID patronId, CallerScope caller, String callerSubject) {
		if (!limiter.tryAcquireForCaller(callerSubject)) {
			return Mono.error(tooMany());
		}

		return patronService.findById(new PatronId(patronId))
			.switchIfEmpty(Mono.error(() -> notFound(caller, "No patron has this id.")))
			.flatMap(patron -> ensureAllowed(caller, patron, null, false).thenReturn(patron))
			.flatMap(patron -> ensureNoUnfinishedRequests(patron).thenReturn(patron))
			.flatMap(patron -> Flux.fromIterable(patron.getPatronIdentities())
				.filter(identity -> !Boolean.TRUE.equals(identity.getHomeIdentity()))
				// One library system at a time: this is staff work, not a sweep
				.concatMap(identity -> ensureVirtual(identity)
					.then(remove(identity, patron, null, callerSubject))
					.onErrorResume(VirtualPatronRemovalRefused.class, refused -> Mono.just(
						new VirtualPatronRemovalResult(identity.getId(), hostLmsCodeOf(identity), refused.getCode(),
							refused.getMessage())))
					.onErrorResume(error -> Mono.just(failed(identity, error))))
				.collectList());
	}

	private Mono<VirtualPatronRemovalResult> remove(PatronIdentity identity, Patron patron,
		@Nullable VirtualPatronRemoval requested, String callerSubject) {

		final var hostLmsCode = hostLmsCodeOf(identity);

		return hostLmsService.getClientFor(hostLmsCode)
			.flatMap(lender -> {
				final var removal = chooseRemoval(lender.virtualPatronRemovals(), requested, hostLmsCode);

				return searchAtLender(lender, patron, identity)
					.flatMap(search -> switch (search) {
						case NAMES_IT -> act(lender, identity, removal);
						case FINDS_NONE -> removal == VirtualPatronRemoval.DELETE
							// The library's system no longer has it, so only DCB's record is left to remove
							? virtualIdentityRecords.forget(identity.getId()).thenReturn("ALREADY_GONE")
							: expireByDcbsRecord(lender, identity);
						case NAMES_ANOTHER -> removal == VirtualPatronRemoval.DELETE
							? Mono.error(notConfirmed("The library's system names a different record as DCB's virtual patron for this borrower."))
							: expireByDcbsRecord(lender, identity);
					});
			})
			.flatMap(outcome -> record(identity, patron, hostLmsCode, outcome, callerSubject)
				.thenReturn(VirtualPatronRemovalResult.of(identity.getId(), hostLmsCode, outcome)));
	}

	private Mono<String> act(HostLmsClient lender, PatronIdentity identity, VirtualPatronRemoval removal) {
		return switch (removal) {
			case DELETE -> lender.deletePatron(identity.getLocalId())
				.then(virtualIdentityRecords.forget(identity.getId()))
				.thenReturn("DELETED");
			case EXPIRE -> expire(lender, identity);
		};
	}

	/**
	 * The borrower's current details no longer lead the library's search to this record, as when
	 * their barcode has changed since it was made. The library's system then checks the record at
	 * DCB's id against the barcode DCB recorded for it, so without one nothing is touched.
	 */
	private Mono<String> expireByDcbsRecord(HostLmsClient lender, PatronIdentity identity) {
		if (identity.getLocalBarcode() == null || identity.getLocalBarcode().isBlank()) {
			return Mono.error(notConfirmed("The library's system does not find this record for the borrower, "
				+ "and DCB recorded no barcode for it to check the record against."));
		}

		return expire(lender, identity);
	}

	private Mono<String> expire(HostLmsClient lender, PatronIdentity identity) {
		return lender.retireVirtualPatron(identity)
			.thenReturn("EXPIRED")
			.onErrorResume(VirtualPatronNotFound.class,
				gone -> virtualIdentityRecords.forget(identity.getId()).thenReturn("ALREADY_GONE"))
			.onErrorMap(NotDcbsVirtualPatronException.class, notDcbs -> notConfirmed(
				"The library's system holds a record at this id that is not the virtual patron DCB made for this borrower."));
	}

	static VirtualPatronRemoval chooseRemoval(Set<VirtualPatronRemoval> supported,
		@Nullable VirtualPatronRemoval requested, String hostLmsCode) {

		if (requested != null) {
			if (!supported.contains(requested)) {
				throw new VirtualPatronRemovalRefused(CONFLICT, "NOT_SUPPORTED",
					"The library system %s cannot %s a virtual patron.".formatted(hostLmsCode, requested.name().toLowerCase()));
			}
			return requested;
		}

		if (supported.contains(VirtualPatronRemoval.DELETE)) {
			return VirtualPatronRemoval.DELETE;
		}

		if (supported.contains(VirtualPatronRemoval.EXPIRE)) {
			return VirtualPatronRemoval.EXPIRE;
		}

		throw new VirtualPatronRemovalRefused(CONFLICT, "NOT_SUPPORTED",
			"The library system %s can neither delete nor expire a virtual patron.".formatted(hostLmsCode));
	}

	private enum Search { NAMES_IT, FINDS_NONE, NAMES_ANOTHER }

	/**
	 * Asked to find DCB's virtual patron for this borrower, does the library's system name this
	 * record? That is each system's own test of what DCB's virtual patron is. Several candidates are refused.
	 */
	private Mono<Search> searchAtLender(HostLmsClient lender, Patron patron, PatronIdentity identity) {
		return lender.findVirtualPatron(patron)
			.map(found -> found.getLocalId() != null && found.getLocalId().contains(identity.getLocalId())
				? Search.NAMES_IT
				: Search.NAMES_ANOTHER)
			.onErrorResume(VirtualPatronNotFound.class, notFound -> Mono.just(Search.FINDS_NONE))
			.onErrorMap(MultipleVirtualPatronsFound.class, multiple -> notConfirmed(
				"The library's system has several records that could be DCB's virtual patron for this borrower. Resolve them there."))
			.defaultIfEmpty(Search.FINDS_NONE);
	}

	private Mono<Void> ensureVirtual(PatronIdentity identity) {
		if (Boolean.TRUE.equals(identity.getHomeIdentity())) {
			return Mono.error(new VirtualPatronRemovalRefused(CONFLICT, "NOT_A_VIRTUAL_PATRON",
				"This is the patron's own library account, not a virtual patron."));
		}

		// A borrower's own account in the same system, recorded under the same id, is never touched
		return Mono.from(patronIdentityRepository.existsByLocalIdAndHostLmsAndHomeIdentity(
				identity.getLocalId(), identity.getHostLms(), true))
			.flatMap(isSomeonesAccount -> isSomeonesAccount
				? Mono.error(new VirtualPatronRemovalRefused(CONFLICT, "NOT_A_VIRTUAL_PATRON",
					"This record is a patron's own library account in that system, not a virtual patron."))
				: Mono.empty());
	}

	private Mono<Void> ensureNoUnfinishedRequests(Patron patron) {
		return Mono.from(patronRequestRepository.countUnfinishedRequestsForPatron(patron.getId()))
			.defaultIfEmpty(0L)
			.flatMap(count -> count > 0
				? Mono.error(new VirtualPatronRemovalRefused(CONFLICT, "PATRON_HAS_ACTIVE_REQUESTS",
					"This borrower has %d request%s that %s not finished, so no virtual patron can be removed."
						.formatted(count, count == 1 ? "" : "s", count == 1 ? "is" : "are")))
				: Mono.empty());
	}

	private Mono<Void> ensureAllowed(CallerScope caller, Patron patron, @Nullable DataHostLms lenderSystem,
		boolean lenderMayAct) {

		if (!caller.requiresNarrowing()) {
			return Mono.empty();
		}

		if (caller.isIncoherent()) {
			return Mono.error(forbidden());
		}

		final var homeAgencyCode = patron.getHomeIdentity()
			.map(PatronIdentity::getResolvedAgency)
			.map(DataAgency::getCode)
			.orElse(null);

		if (homeAgencyCode != null && caller.agencyCodes().contains(homeAgencyCode)) {
			return Mono.empty();
		}

		final var lenderSystemId = getValueOrNull(lenderSystem, DataHostLms::getId);

		if (!lenderMayAct || lenderSystemId == null) {
			return Mono.error(forbidden());
		}

		return Flux.from(agencyRepository.findByCodeIn(caller.agencyCodes()))
			.map(agency -> getValueOrNull(agency, DataAgency::getHostLms, DataHostLms::getId))
			.filter(Objects::nonNull)
			.any(lenderSystemId::equals)
			.flatMap(isTheirs -> isTheirs ? Mono.<Void>empty() : Mono.error(forbidden()));
	}

	private Mono<Event> record(PatronIdentity identity, Patron patron, String hostLmsCode, String outcome,
		String callerSubject) {

		final var data = new HashMap<String, Object>();
		data.put("identityId", identity.getId().toString());
		data.put("patronId", patron.getId().toString());
		data.put("hostLmsCode", hostLmsCode);
		data.put("outcome", outcome);
		data.put("staff", callerSubject);

		return Mono.from(eventLogRepository.save(Event.builder()
			.id(UUID.randomUUID())
			.type(EventType.VIRTUAL_PATRON_REMOVED)
			.summary("Virtual patron %s at %s".formatted(outcome, hostLmsCode))
			.additionalData(data)
			.build()));
	}

	private static VirtualPatronRemovalResult failed(PatronIdentity identity, Throwable error) {
		log.warn("Removing virtual patron identity {} at {} failed", identity.getId(), hostLmsCodeOf(identity), error);

		return new VirtualPatronRemovalResult(identity.getId(), hostLmsCodeOf(identity), "FAILED",
			"The library's system did not remove it. The DCB log has the reason under identity " + identity.getId() + ".");
	}

	private static String hostLmsCodeOf(PatronIdentity identity) {
		return getValueOrNull(identity, PatronIdentity::getHostLms, DataHostLms::getCode);
	}

	private static VirtualPatronRemovalRefused notConfirmed(String message) {
		return new VirtualPatronRemovalRefused(CONFLICT, "NOT_CONFIRMED_AT_LIBRARY", message);
	}

	private static VirtualPatronRemovalRefused tooMany() {
		return new VirtualPatronRemovalRefused(TOO_MANY_REQUESTS, "TOO_MANY_REMOVALS",
			"You have removed as many virtual patrons as one person can in a minute.", VirtualPatronRemovalLimiter.WINDOW);
	}

	// A library caller is told 403 for something that does not exist too, so it cannot probe ids
	private static VirtualPatronRemovalRefused notFound(CallerScope caller, String message) {
		return caller.requiresNarrowing() ? forbidden() : new VirtualPatronRemovalRefused(NOT_FOUND, "NOT_FOUND", message);
	}

	private static VirtualPatronRemovalRefused forbidden() {
		return new VirtualPatronRemovalRefused(FORBIDDEN, "FORBIDDEN",
			"Only the borrower's library, the library whose system holds the virtual patron, or consortium staff can do this.");
	}
}
