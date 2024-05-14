package org.olf.dcb.item.availability;

import static java.util.function.UnaryOperator.identity;
import static org.olf.dcb.item.availability.AvailabilityReport.emptyReport;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.ItemStatus;
import org.olf.dcb.core.model.ItemStatusCode;
import org.olf.dcb.request.resolution.ClusteredBib;
import org.olf.dcb.request.resolution.NoBibsForClusterRecordException;
import org.olf.dcb.request.resolution.SharedIndexService;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.micrometer.common.lang.NonNull;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import services.k_int.utils.TupleUtils;

@Slf4j
@Singleton
public class LiveAvailabilityService {
	private final HostLmsService hostLmsService;
	private final RequestableItemService requestableItemService;
	private final SharedIndexService sharedIndexService;
	
	final Cache<String, AvailabilityReport> availabilityCache = Caffeine.newBuilder()
			.maximumSize(1_000) // Maximum number of entries
			.expireAfterAccess(Duration.ofDays(1)) // Consider 24 hour old data to be stale enough for complete eviction
			.build();

	public LiveAvailabilityService(HostLmsService hostLmsService,
		RequestableItemService requestableItemService, SharedIndexService sharedIndexService) {

		this.hostLmsService = hostLmsService;
		this.requestableItemService = requestableItemService;
		this.sharedIndexService = sharedIndexService;
	}
	
	private Flux<BibRecord> getClusterMembers( @NonNull UUID clusteredBibId ) {
		return Mono.just(clusteredBibId)
			.flatMap( sharedIndexService::findClusteredBib )
			.flatMapIterable(ClusteredBib::getBibs)
			.doOnNext ( b -> log.trace( "Cluster has bib members" ))
			.switchIfEmpty(Mono.defer(() -> Mono.error( new NoBibsForClusterRecordException(clusteredBibId)) ));
	}
	
	private boolean shouldCache( AvailabilityReport report ) {
		if (report == null) return false;
		
		// Don't cache errors from single service.
		return report.getErrors().size() < 1; 
	}
	
	private AvailabilityReport addValueToCache ( @NonNull BibRecord bib, AvailabilityReport report) {
		
		return Optional.ofNullable(report)
			.filter(this::shouldCache)
			.map( ar -> {
				String bibIdStr = bib.getId().toString();
				log.info("Caching availability for bibId: [{}]", bibIdStr);
				availabilityCache.put(bibIdStr, ar);
				return ar;
			})
			
			// Always return the original for chaining.
			.orElse(report);
	}

	public Mono<AvailabilityReport> checkAvailability(UUID clusteredBibId) {
		return checkAvailability( clusteredBibId, Optional.empty() );
	}
	
	public Mono<AvailabilityReport> checkAvailability(UUID clusteredBibId, Duration timeout) {
		return checkAvailability(clusteredBibId, Optional.ofNullable(timeout));
	}
	
	private Mono<AvailabilityReport> checkAvailability(UUID clusteredBibId, Optional<Duration> timeout) {
		log.debug("getAvailableItems({})", clusteredBibId);

		return Mono.just( clusteredBibId )
			.flatMapMany( this::getClusterMembers )
			.flatMap( b -> checkBibAvailabilityAtHost(timeout, b))
			.map( this::determineRequestability )
			.doOnNext ( b -> log.debug("Requestability check result == {}",b) )
			.reduce(emptyReport(), AvailabilityReport::combineReports)
			.doOnNext ( b -> log.debug("Sorting..."))
			.map(AvailabilityReport::sortItems)
			.switchIfEmpty(
				Mono.defer(() -> {
					log.error("getAvailableItems resulted in an empty stream");
					return Mono.error(new RuntimeException("Failed to resolve items for cluster record " + clusteredBibId));
				})
			);
	}

	private Mono<AvailabilityReport> checkBibAvailabilityAtHost(
		Optional<Duration> timeout, BibRecord bibRecord) {
		
		return hostLmsService.getClientFor(bibRecord.getSourceSystemId())
		  .flatMap(TupleUtils.curry(timeout, bibRecord, this::checkBibAvailabilityAtHost))
			.doOnNext(b -> log.debug("getAvailableItems got items, progress to availability check"));
	}

	private Mono<AvailabilityReport> checkBibAvailabilityAtHost(
		Optional<Duration> timeout, BibRecord bib, HostLmsClient hostLms) {
		
		final var liveData = hostLms.getItems(bib)
			.flatMapIterable(identity())
			.filter(Item::notSuppressed)
			.filter(Item::notDeleted)
			.filter(Item::hasAgency)
			.collectList()
			.doOnError(error -> log.error("doOnError occurred fetching items", error))
			.map(AvailabilityReport::ofItems)
			.map(TupleUtils.curry(bib, this::addValueToCache))
			.onErrorResume(error -> Mono.defer(() ->
				Mono.just(AvailabilityReport.ofErrors(mapToError(bib, hostLms.getHostLmsCode())))))
			.cache(); // Create a hot source and cache. This will make sure the mono completes as hot sources cannot be cancelled;
		
		return timeout
			.map(timeoutSet -> liveData.transformDeferred(addCacheFallback(timeoutSet, bib, hostLms)))
			.orElse(liveData);
	}

	private Function<Mono<AvailabilityReport>, Mono<AvailabilityReport>> addCacheFallback(
		Duration timeout, BibRecord bib, HostLmsClient hostLms) {

		return (liveData) -> {
			Mono<AvailabilityReport> cachedData = getFromCache( bib )
				.delaySubscription(timeout)
				.doOnCancel(() -> log.trace("Request for bib {} from host lms {} completed in time. Cancelled cache subscription.", bib.getId(), hostLms.getHostLmsCode()) )
				.doOnSuccess(_ar -> log.info("Request for bib {} from host lms {} did not complete in time.", bib.getId(), hostLms.getHostLmsCode()) )
				.onErrorResume (error -> Mono.defer(() -> {
					log.error("Error fetching bib {} for host lms {} from cache.", bib.getId(), hostLms.getHostLmsCode());
					return Mono.empty();
				}))
				.switchIfEmpty(Mono.defer(() -> Mono.just(
					getNoCachedValueErrorReport("Unable to fetch live availability from " + hostLms.getHostLmsCode() + ", and no previously cached value to fall back on."))));

				// Create a delayed mono that will emit the cached value (if present) after the timeout duration.
				return Mono.firstWithSignal(liveData, cachedData);
		};
	}
	
	private Mono<AvailabilityReport> getFromCache(BibRecord bib) {
		return Mono.just(bib.getId().toString())
			.mapNotNull(availabilityCache::getIfPresent)
			.map( this::modifyCachedRecord );
	}
	
	private static final ItemStatus STATUS_UNKNOWN = new ItemStatus(ItemStatusCode.UNKNOWN);
	
	private AvailabilityReport modifyCachedRecord (AvailabilityReport ar) {
		
		// Build new item lists and set the status to unknown.
		List<Item> newItems = new ArrayList<>();
		ar.getItems().stream()
			.map( Item::toBuilder )
			.map( itemBuilder -> itemBuilder
				.status(STATUS_UNKNOWN)
				.build())
			.forEach( newItems::add );
		
		return ar.toBuilder()
			.items(newItems)
			.build();
	}
	
	private static final AvailabilityReport getNoCachedValueErrorReport ( String message ) {
		return Optional.of(message)
			.map(msg -> AvailabilityReport.Error.builder().message(msg).build())
			.map(err -> AvailabilityReport.of(List.of(), List.of(err)))
			.get();
	}

	private AvailabilityReport determineRequestability( AvailabilityReport report ) {
		return report.forEachItem(
			item -> item.setIsRequestable(requestableItemService.isRequestable(item)));
	}

	private static AvailabilityReport.Error mapToError(BibRecord bib, String hostLmsCode) {
		log.error("Generate error report : Failed to fetch items for bib: {} from host: {}",
			bib.getSourceRecordId(), hostLmsCode);

		return AvailabilityReport.Error.builder()
			.message(String.format("Failed to fetch items for bib: %s from host: %s",
				bib.getSourceRecordId(), hostLmsCode))
			.build();
	}
}
