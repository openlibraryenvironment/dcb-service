package org.olf.dcb.request.fulfilment;

import static org.olf.dcb.core.interaction.PingFailure.MISCONFIGURED;
import static org.olf.dcb.core.interaction.PingFailure.REFUSED;
import static org.olf.dcb.core.interaction.PingFailure.UNREACHABLE;
import static org.olf.dcb.core.interaction.PingResponse.ERROR;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.SupplierPreflight;
import org.olf.dcb.core.interaction.shared.NoPatronTypeMappingFoundException;
import org.olf.dcb.core.model.InvalidHostLmsConfigurationException;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.core.svc.HostLmsPingService;

import io.micronaut.context.annotation.Value;
import io.micronaut.context.exceptions.BeanInstantiationException;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Whether a supplier can take a request, decided from what DCB holds: the supplier is called only to
 * re-take a ping that ruled it out. Only a fault in the supplier's configuration, which DCB cannot fix
 * itself, rules it out. Anything this cannot establish lets the request go ahead.
 */
@Slf4j
@Singleton
public class SupplierPreflightService {
	// The scheduled ping runs once a day (dcb.task.housekeeping.interval), so a refusal it saw may
	// since have been fixed at the supplier; one older than this is re-taken before it rules a supplier out
	static final Duration RULING_PING_TRUSTED_FOR = Duration.ofMinutes(15);
	static final Duration REPING_TIMEOUT = Duration.ofSeconds(10);

	private static final Set<String> RULING_FAILURES = Set.of(REFUSED, MISCONFIGURED);

	private final HostLmsService hostLmsService;
	private final HostLmsPingService hostLmsPingService;
	private final boolean enabled;

	public SupplierPreflightService(HostLmsService hostLmsService, HostLmsPingService hostLmsPingService,
		@Value("${dcb.requests.supplier-preflight.enabled:false}") boolean enabled) {

		this.hostLmsService = hostLmsService;
		this.hostLmsPingService = hostLmsPingService;
		this.enabled = enabled;

		log.info(enabled
			? "Supplier preflight is on: a supplier whose last ping was refused or misconfigured is skipped"
			: "Supplier preflight is off: requests are placed at a supplier whatever its setup or last ping. "
				+ "DCB_REQUESTS_SUPPLIER_PREFLIGHT_ENABLED=true turns it on");
	}

	/** The supplier's own faults, the same for every request. Resolution skips its copies on these. */
	public Mono<SupplierPreflight> checkSupplier(String supplierHostLmsCode) {
		if (!enabled) {
			return Mono.just(SupplierPreflight.notChecked());
		}

		// Not the settings an adapter declares mandatory: some, such as Sierra's ingest, do not stop it supplying
		return hostLmsService.getClientFor(supplierHostLmsCode)
			.flatMap(client -> lastPing(supplierHostLmsCode))
			// Only a client that cannot be built is the supplier's fault; a database error is not
			.onErrorResume(error -> isConfigurationFault(error)
				? Mono.just(SupplierPreflight.cannotSupply(
					"DCB cannot connect to Host LMS %s with its configuration: %s"
						.formatted(supplierHostLmsCode, ExceptionUtils.getRootCauseMessage(error)),
					"Correct the configuration of Host LMS %s in DCB Admin; saving it reports what is wrong"
						.formatted(supplierHostLmsCode)))
				: notEstablished(supplierHostLmsCode, error))
			.defaultIfEmpty(SupplierPreflight.clear());
	}

	public static SupplierPreflight.Query queryFor(RequestWorkflowContext context) {
		return new SupplierPreflight.Query(
			getValueOrNull(context, RequestWorkflowContext::getPatronAgencyCode),
			getValueOrNull(context, RequestWorkflowContext::getLenderAgencyCode),
			getValueOrNull(context, RequestWorkflowContext::getSupplierRequest, SupplierRequest::getLocalItemType),
			getValueOrNull(getValueOrNull(context, RequestWorkflowContext::getPatronRequest,
				PatronRequest::getRequestingIdentity), PatronIdentity::getCanonicalPtype));
	}

	/** Everything, for one request about to be placed at the supplier. */
	public Mono<SupplierPreflight> check(String supplierHostLmsCode, SupplierPreflight.Query query) {
		if (!enabled) {
			return Mono.just(SupplierPreflight.notChecked());
		}

		return checkSupplier(supplierHostLmsCode)
			.flatMap(supplier -> supplier.cannotSupply()
				? Mono.just(supplier)
				: forThisRequest(supplierHostLmsCode, query).defaultIfEmpty(supplier));
	}

	private Mono<SupplierPreflight> forThisRequest(String supplierHostLmsCode, SupplierPreflight.Query query) {
		return hostLmsService.getClientFor(supplierHostLmsCode)
			.flatMap(client -> unmappedPatronType(client, supplierHostLmsCode, query.canonicalPatronType())
				.switchIfEmpty(Mono.defer(() -> client.supplierPreflight(query)
					.filter(SupplierPreflight::cannotSupply))))
			.onErrorResume(error -> {
				log.warn("Unable to check whether Host LMS {} can supply a request ({}), placing it",
					supplierHostLmsCode, error.toString());

				return Mono.empty();
			});
	}

	private Mono<SupplierPreflight> lastPing(String supplierHostLmsCode) {
		final var now = Instant.now();

		return hostLmsPingService.lastPingOf(supplierHostLmsCode)
			.flatMap(ping -> isUntrustedRulingFailure(ping, now)
				? repinged(supplierHostLmsCode)
				: Mono.just(ping))
			// Not re-taken: that would add a timeout to placement exactly while the supplier is down
			.filter(ping -> !isStaleUnreachable(ping, now))
			.mapNotNull(ping -> fromLastPing(supplierHostLmsCode, ping))
			.onErrorResume(error -> Mono.empty());
	}

	// A re-ping that cannot finish in time establishes nothing, so the supplier is not ruled out
	private Mono<Map<String, Object>> repinged(String supplierHostLmsCode) {
		return hostLmsPingService.pingAndRecord(supplierHostLmsCode)
			.timeout(REPING_TIMEOUT)
			.then(Mono.defer(() -> hostLmsPingService.lastPingOf(supplierHostLmsCode)))
			.onErrorResume(error -> {
				log.warn("Unable to re-ping Host LMS {} before ruling it out ({}), not ruling it out",
					supplierHostLmsCode, error.toString());

				return Mono.empty();
			});
	}

	static boolean isUntrustedRulingFailure(Map<String, Object> ping, Instant now) {
		if (!ERROR.equals(ping.get("status")) || !RULING_FAILURES.contains(String.valueOf(ping.get("failure")))) {
			return false;
		}

		try {
			return Instant.parse(String.valueOf(ping.get("checkedAt"))).isBefore(now.minus(RULING_PING_TRUSTED_FOR));
		}
		catch (DateTimeParseException unreadable) {
			return true;
		}
	}

	// The daily ping's UNREACHABLE would otherwise warn on every placement at that supplier until the next one
	static boolean isStaleUnreachable(Map<String, Object> ping, Instant now) {
		if (!ERROR.equals(ping.get("status")) || !UNREACHABLE.equals(String.valueOf(ping.get("failure")))) {
			return false;
		}

		try {
			return Instant.parse(String.valueOf(ping.get("checkedAt"))).isBefore(now.minus(RULING_PING_TRUSTED_FOR));
		}
		catch (DateTimeParseException unreadable) {
			return true;
		}
	}

	private static boolean isConfigurationFault(Throwable error) {
		return error instanceof InvalidHostLmsConfigurationException
			|| error instanceof BeanInstantiationException;
	}

	private static Mono<SupplierPreflight> notEstablished(String supplierHostLmsCode, Throwable error) {
		log.warn("Unable to check whether Host LMS {} can supply ({}), not ruling it out",
			supplierHostLmsCode, error.toString());

		return Mono.empty();
	}

	private static SupplierPreflight fromLastPing(String supplierHostLmsCode, Map<String, Object> ping) {
		if (!ERROR.equals(ping.get("status"))) {
			return null;
		}

		final var when = ping.get("checkedAt");

		// Not the ping's detail: it is scoped to the supplier's administrators, and these reasons
		// reach the borrowing library's request audit and error message
		return switch (String.valueOf(ping.get("failure"))) {
			case REFUSED -> SupplierPreflight.cannotSupply(
				"At its last check (%s) Host LMS %s refused DCB's credentials".formatted(when, supplierHostLmsCode),
				"Correct the credentials for Host LMS %s in DCB Admin, then save it so DCB checks again"
					.formatted(supplierHostLmsCode));
			case MISCONFIGURED -> SupplierPreflight.cannotSupply(
				"At its last check (%s) Host LMS %s could not be used with its configuration"
					.formatted(when, supplierHostLmsCode),
				"Correct the configuration of Host LMS %s in DCB Admin, then save it so DCB checks again"
					.formatted(supplierHostLmsCode));
			case UNREACHABLE -> SupplierPreflight.unreachable(
				"At its last check (%s) DCB could not reach Host LMS %s".formatted(when, supplierHostLmsCode));
			default -> null;
		};
	}

	// Sierra answers empty for a missing mapping; Alma, FOLIO, Koha and Polaris raise an error
	private static Mono<SupplierPreflight> unmappedPatronType(HostLmsClient client, String supplierHostLmsCode,
		String canonicalPatronType) {

		if (canonicalPatronType == null) {
			return Mono.empty();
		}

		return client.findLocalPatronType(canonicalPatronType)
			.hasElement()
			.onErrorResume(NoPatronTypeMappingFoundException.class, error -> Mono.just(false))
			.filter(mapped -> !mapped)
			.map(unmapped -> SupplierPreflight.cannotSupply(
				"Host LMS %s has no patron type mapped from DCB patron type %s"
					.formatted(supplierHostLmsCode, canonicalPatronType),
				"Add a patron type mapping from DCB patron type %s to a patron type at Host LMS %s"
					.formatted(canonicalPatronType, supplierHostLmsCode)));
	}
}
