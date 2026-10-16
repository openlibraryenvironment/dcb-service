package org.olf.dcb.core.api;

import static io.micronaut.http.HttpResponse.badRequest;
import static io.micronaut.http.HttpResponse.serverError;
import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static io.micronaut.security.rules.SecurityRule.IS_ANONYMOUS;
import static org.olf.dcb.item.availability.AvailabilityOptions.useCache;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.UnknownHostLmsException;
import org.olf.dcb.item.availability.AvailabilityResponseView;
import org.olf.dcb.item.availability.BatchAvailabilityView;
import org.olf.dcb.item.availability.AvailabilityResponseViewV2;
import org.olf.dcb.item.availability.LiveAvailabilityService;
import org.olf.dcb.request.resolution.CannotFindClusterRecordException;

import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.annotation.SingleResult;
import org.olf.dcb.security.OpenToAllPrincipals;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.security.annotation.Secured;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Controller
@Validated
@Secured(IS_ANONYMOUS)
@OpenToAllPrincipals(justification =
	"Deliberately public: discovery layers ask for availability before anyone signs in. It returns item status and location, no patron data, and confers no authority. Its defence is its bounds: a batch is capped at dcb.live-availability.batch.max-clusters clusters, read dcb.live-availability.batch.concurrency at a time, each within dcb.live-availability.timeout.")
@Tag(name = "Live Availability API")
public class LiveAvailabilityController {
	private final LiveAvailabilityService liveAvailabilityService;
	
	@Value("${dcb.live-availability.timeout:PT7S}")
	protected Duration timeout;

	/**
	 * How many clusters one batch request may ask about.
	 *
	 * <p>The bound that does not exist when a caller loops over the single-cluster route:
	 * there, N is whatever the caller chose, and this service cannot see that the N calls
	 * are one page of one list.
	 */
	@Value("${dcb.live-availability.batch.max-clusters:25}")
	protected int maxClustersPerBatch;

	/**
	 * How many of those are checked at once.
	 *
	 * <p>Every cluster is a fan-out to each contributing library, so the OUTBOUND concurrency
	 * is this times the members of a cluster. Four is chosen to keep that in the tens against
	 * systems we do not own, some of them decades old.
	 */
	@Value("${dcb.live-availability.batch.concurrency:4}")
	protected int batchConcurrency;

	public LiveAvailabilityController(LiveAvailabilityService liveAvailabilityService) {
		this.liveAvailabilityService = liveAvailabilityService;
	}

	@Operation(
		summary = "Live Item Availability",
		description = "Check Live Item Availability for a Bibliographic Record"
	)
	@SingleResult
	@Get(value = "/items/availability", produces = APPLICATION_JSON)
	public Mono<AvailabilityResponseView> getLiveAvailability(
		@NotNull @QueryValue("clusteredBibId") final UUID clusteredBibId,
		@Nullable @QueryValue("filters") final String filters) {
		log.info("REST, getLiveAvailability: {} {}", clusteredBibId, filters);

		return liveAvailabilityService.checkAvailability(clusteredBibId, useCache(timeout, filters))
			.map(report -> AvailabilityResponseView.from(report, clusteredBibId));
	}

	@Operation(
		summary = "Live Item Availability v2",
		description = "Check Live Item Availability including electronic-item metadata"
	)
	@SingleResult
	@Get(value = "/items/availability-v2", produces = APPLICATION_JSON)
	public Mono<AvailabilityResponseViewV2> getLiveAvailabilityV2(
		@NotNull @QueryValue("clusteredBibId") final UUID clusteredBibId,
		@Nullable @QueryValue("filters") final String filters) {
		log.info("REST, getLiveAvailabilityV2: {} {}", clusteredBibId, filters);

		return liveAvailabilityService.checkAvailability(clusteredBibId, useCache(timeout, filters))
			.map(report -> AvailabilityResponseViewV2.from(report, clusteredBibId));
	}

	@Operation(
		summary = "Live Item Availability for several clusters",
		description = "Availability for up to dcb.live-availability.batch.max-clusters "
			+ "clusters in one request. Exists so the fan-out has a bound: a caller looping "
			+ "over /items/availability makes N independent requests, and this service cannot "
			+ "see that they are one page of one reading list. Partial by design - a cluster "
			+ "that could not be checked appears in 'failures' rather than failing the batch, "
			+ "because 'we could not look' and 'we looked and found none' are different "
			+ "answers to a patron."
	)
	@SingleResult
	@Get(value = "/items/availability/batch", produces = APPLICATION_JSON)
	public Mono<BatchAvailabilityView> getBatchLiveAvailability(
		@NotNull @QueryValue("clusteredBibId") final List<UUID> clusteredBibIds,
		@Nullable @QueryValue("filters") final String filters) {

		log.info("REST, getBatchLiveAvailability: {} clusters {}", clusteredBibIds.size(), filters);

		if (clusteredBibIds.size() > maxClustersPerBatch) {
			return Mono.error(new TooManyClustersException(
				clusteredBibIds.size(), maxClustersPerBatch));
		}

		final var options = useCache(timeout, filters);

		// Duplicates removed before anything is asked: the same cluster twice in one list is
		// a caller's mistake and must not become two fan-outs.
		final var wanted = clusteredBibIds.stream().distinct().toList();

		return Flux.fromIterable(wanted)
			// flatMapSequential with an EXPLICIT concurrency, so results keep the order they
			// were asked for while the outbound work stays bounded. flatMap would return
			// them in completion order, which would make a reading list reorder itself.
			.flatMapSequential(id -> checkOne(id, options), batchConcurrency)
			.collectList()
			.map(outcomes -> new BatchAvailabilityView(
				outcomes.stream().map(Outcome::view).filter(java.util.Objects::nonNull).toList(),
				outcomes.stream().map(Outcome::failure).filter(java.util.Objects::nonNull).toList()));
	}

	/**
	 * One cluster, and never an error that fails the batch.
	 *
	 * <p>A single unreachable host LMS must not cost a patron the other nineteen rows of
	 * their list.
	 */
	private Mono<Outcome> checkOne(UUID clusteredBibId,
		org.olf.dcb.item.availability.AvailabilityOptions options) {

		return liveAvailabilityService.checkAvailability(clusteredBibId, options)
			.map(report -> new Outcome(
				AvailabilityResponseView.from(report, clusteredBibId), null))
			.onErrorResume(error -> {
				log.warn("Batch availability could not check cluster {}", clusteredBibId, error);
				return Mono.just(new Outcome(null,
					BatchAvailabilityView.unchecked(clusteredBibId, error)));
			});
	}

	private record Outcome(AvailabilityResponseView view,
		BatchAvailabilityView.Unchecked failure) {
	}

	/** Asking about more clusters than the bound allows is a client mistake, not a 500. */
	static class TooManyClustersException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		TooManyClustersException(int asked, int allowed) {
			super("Asked about " + asked + " clusters; this deployment allows " + allowed
				+ " per request. Page the list rather than raising the limit.");
		}
	}

	@Error
	public HttpResponse<String> onTooManyClusters(TooManyClustersException exception) {
		return badRequest(exception.getMessage());
	}

	@Error
	public HttpResponse<String> onUnknownClusterRecord(CannotFindClusterRecordException exception) {
		return badRequest(exception.getMessage());
	}

	@Error
	public HttpResponse<String> onUnknownHostLms(UnknownHostLmsException exception) {
		return serverError(exception.getMessage());
	}

}
