package org.olf.dcb.item.availability;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.function.UnaryOperator.identity;
import static org.olf.dcb.item.availability.AvailabilityReport.emptyReport;
import static org.olf.dcb.item.availability.AvailabilityReport.ofItems;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

import org.olf.dcb.availability.job.AvailabilityCheckJob;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.svc.LocationService;
import org.olf.dcb.request.resolution.AvailabilityDateCalculator;
import org.olf.dcb.request.resolution.ClusteredBib;
import org.olf.dcb.request.resolution.SharedIndexService;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.micrometer.common.lang.NonNull;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micronaut.context.BeanProvider;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;
import services.k_int.utils.Functions;

@Slf4j
@Singleton
public class LiveAvailabilityService {
	private final HostLmsService hostLmsService;
	private final RequestableItemService requestableItemService;
	private final SharedIndexService sharedIndexService;
	private final LocationService locationService;
	private final MeterRegistry meterRegistry; 
	private final BeanProvider<AvailabilityCheckJob> availability;
	
	private static final String METRIC_NAME = "dcb.availability";
	
	final Cache<String, AvailabilityReport> availabilityCache = Caffeine.newBuilder()
			.maximumSize(1_000) // Maximum number of entries
			.expireAfterAccess(Duration.ofDays(1)) // Consider 24 hour old data to be stale enough for complete eviction
			.build();

	public LiveAvailabilityService(HostLmsService hostLmsService,
		RequestableItemService requestableItemService,
		SharedIndexService sharedIndexService, 
		MeterRegistry meterRegistry,
		LocationService locationService, BeanProvider<AvailabilityCheckJob> availability) {

		this.hostLmsService = hostLmsService;
		this.requestableItemService = requestableItemService;
		this.sharedIndexService = sharedIndexService;
		this.meterRegistry = meterRegistry;
		this.locationService = locationService;
		this.availability = availability;
	}
	
	private Flux<BibRecord> getClusterMembers(@NonNull UUID clusteredBibId) {
		return Mono.just(clusteredBibId)
			.flatMap(sharedIndexService::findClusteredBib)
			.flatMapIterable(ClusteredBib::getBibs);
	}
	
	private boolean shouldCache(AvailabilityReport report) {
		if (report == null) return false;
		
		// Don't cache errors from single service.
		return report.getErrors().size() < 1;
	}
	
	private Mono<AvailabilityReport> tryAndUpdateCounts( @NonNull BibRecord bib, @NonNull AvailabilityReport report ) {
		
		if (!availability.isPresent() ) {
			// Not present. i.e. During tests
			return Mono.just(report);
		}
		
		log.info("Try update counts for bib [{}] from live lookup data", bib.getId());
		return availability.get()
			.updateCountsForSingleBibAvailability(bib, report)
			.thenReturn(report)
			.onErrorReturn(report)
			.doOnError( e -> log.warn("Error updating counts from returned data in live lookup"));
	}
	
	private Mono<AvailabilityReport> addValueToCache(@NonNull BibRecord bib, AvailabilityReport report) {
		
		return Mono.justOrEmpty(report)
			// Attempt to update the counts from the data here.
			.flatMap( ar -> tryAndUpdateCounts(bib, ar) )
			.filter(this::shouldCache)
			.map( ar -> {
				String bibIdStr = bib.getId().toString();
				log.debug("Caching availability for bibId: [{}]", bibIdStr);
				availabilityCache.put(bibIdStr, ar);
				return ar;
			})
			
			// Always return the original for chaining.
			.defaultIfEmpty(report);
	}



	public Mono<AvailabilityReport> checkAvailabilityNoCache(UUID clusteredBibId, Optional<Duration> timeout) {
		return checkAvailability(clusteredBibId, timeout, Optional.of("all"), true );
	}
	
	public Mono<AvailabilityReport> checkAvailability(UUID clusteredBibId,
		Optional<Duration> timeout) {

		return checkAvailability(clusteredBibId, timeout, Optional.of("all"), false );
	}
	
	public Mono<AvailabilityReport> checkAvailability(UUID clusteredBibId,
		Duration timeout, String filters) {

		return checkAvailability(clusteredBibId, Optional.ofNullable(timeout),
			Optional.ofNullable(filters), false);
	}
	
	public Mono<AvailabilityReport> checkBibAvailability (BibRecord bib, Duration timeout, String filters) {
		return checkBibAvailabilityAtHost(Optional.ofNullable(timeout), bib, Collections.emptyList(), Optional.ofNullable(filters), false);
	}
		
	private Mono<AvailabilityReport> checkAvailability(UUID clusteredBibId,
		Optional<Duration> timeout, Optional<String> filters, boolean ignoreCache) {

		log.debug("getAvailableItems({})", clusteredBibId);

		// This has to happen at the start of the process, in order for available items
		// to get the same availability date
		final var availabilityDateCalculator = new AvailabilityDateCalculator();

		final List<Tag> commonTags = List.of(Tag.of("cluster", clusteredBibId.toString()));

		return Mono.defer(() -> Mono.just(System.nanoTime()))
			.flatMap(start -> Mono.just(clusteredBibId)
				.flatMapMany(this::getClusterMembers)
				.flatMap(b -> checkBibAvailabilityAtHost(timeout, b, commonTags, filters, ignoreCache))
				.doOnNext(b -> log.debug("Requestability check result == {}", b))
				.reduce(emptyReport(), AvailabilityReport::combineReports)
				.flatMap(availabilityReport -> calculateFields(availabilityReport,
					availabilityDateCalculator))
				.doOnNext(b -> log.debug("Sorting..."))
				.map(AvailabilityReport::sortItems)
				.map(report -> reportElapsedTime(report, start))
				.switchIfEmpty(Mono.defer(() -> {
						log.error("getAvailableItems resulted in an empty stream");
						return Mono.error(new RuntimeException("Failed to resolve items for cluster record " + clusteredBibId));
					})
				));
	}

	private Mono<AvailabilityReport> calculateFields(AvailabilityReport availabilityReport,
		AvailabilityDateCalculator availabilityDateCalculator) {

		return Flux.fromIterable(availabilityReport.getItems())
			.flatMap(this::calculateRequestability)
			.map(item -> calculateAvailabilityDate(item, availabilityDateCalculator))
			.collectList()
			.map(items -> ofItems(items, availabilityReport.getTimings(), availabilityReport.getErrors()));
	}

	private Item calculateAvailabilityDate(Item item,
		AvailabilityDateCalculator availabilityDateCalculator) {

		return item.setAvailableDate(availabilityDateCalculator.calculate(item));
	}

	// set an item's requestability
	private Mono<Item> calculateRequestability(Item item) {
		return requestableItemService.isRequestable(item)
			.map(item::setIsRequestable);
	}

	private static AvailabilityReport reportElapsedTime(AvailabilityReport report, Long start) {
		final long elapsed = System.nanoTime() - start;

		return report.toBuilder()
			.timing(Tuples.of("total", elapsed))
			.build();
	}

	private Mono<AvailabilityReport> checkBibAvailabilityAtHost(
		Optional<Duration> timeout, BibRecord bibRecord, List<Tag> parentTags, Optional<String> filters, boolean ignoreCache) {
		
		return hostLmsService.getClientFor(bibRecord.getSourceSystemId())
		  .flatMap(hostLms -> checkBibAvailabilityAtHost(timeout, bibRecord, parentTags, hostLms, filters, ignoreCache))
			.doOnNext(b -> log.debug("getAvailableItems got items, progress to availability check"));
	}
	
	/**
	 * This method allows the filter options of checkBibAvailabilityAtHost to be programatically controlled
	 * by the caller. Initially this is a binary all (The default) or non - to switch off filtering and allow
	 * the service to report the actual availability of all items, rather than silently dropping non-available
	 * ones.
	 */
	private Predicate<Item> conditionallyFilter(Optional<String> filters, Predicate<Item> filterPredicate) {
		// If filters is not set, the default is all
		// If the value of filters is "none" return true, effectively bypassing all filters
		return item -> ( (filters.orElse("all").equalsIgnoreCase("none" ) ) || filterPredicate.test(item) );
	}

	private Mono<AvailabilityReport> checkBibAvailabilityAtHost(
		Optional<Duration> timeout, BibRecord bib, List<Tag> parentTags, HostLmsClient hostLms, Optional<String> filters, boolean ignoreCache) {

		// Removing bib ID from metric as we need 1 entry per system and task rather one for every bib.
		final List<Tag> commonTags = new ArrayList<>(List.of(Tag.of("lms", hostLms.getHostLmsCode())));
//		final List<Tag> commonTags = new ArrayList<>(List.of(Tag.of("bib", bib.getId().toString()), Tag.of("lms", hostLms.getHostLmsCode())));
		commonTags.addAll(parentTags);
		
		final var liveData = Mono.defer( () -> Mono.just(System.nanoTime()) )
			.flatMap( start -> hostLms.getItems(bib)
					.flatMapIterable(identity())
					.flatMap( item -> memoizeLocationFromItem(item) )
					.filter(conditionallyFilter(filters, Item::notSuppressed))
					.filter(conditionallyFilter(filters, Item::notDeleted))
					.filter(conditionallyFilter(filters, Item::hasAgency))
					.filter(conditionallyFilter(filters, Item::hasHostLms))
					.filter(conditionallyFilter(filters, Item::AgencyIsSupplying))
					.collectList()
					.map(AvailabilityReport::ofItems)
					.flatMap(Functions.curry(bib, this::addValueToCache))
					.map( report -> {
						final long elapsed = System.nanoTime() - start;
						var tags = new ArrayList<>(List.of( Tag.of("status", "success") ));
						tags.addAll(commonTags);
						Timer timer = meterRegistry.timer(METRIC_NAME, tags);
						timer.record(System.nanoTime() - start, NANOSECONDS);
						
						return report.toBuilder()
								.timing( Tuples.of(hostLms.getHostLmsCode(), elapsed) )
								.build();
					})
					.onErrorResume(error -> Mono.defer(() -> {
						final long elapsed = System.nanoTime() - start;
						log.error("Error fetching items"+error.getMessage());
						var tags = new ArrayList<>(List.of( Tag.of("status", "error") ));
						tags.addAll(commonTags);
						Timer timer = meterRegistry.timer(METRIC_NAME, tags);
						timer.record(System.nanoTime() - start, NANOSECONDS);
						return Mono.just(AvailabilityReport.ofErrors(mapToError(bib, hostLms.getHostLmsCode()), Tuples.of(hostLms.getHostLmsCode(), elapsed) ));
					})))
			.cache();
		
		return timeout
			.map(timeoutSet -> liveData.transformDeferred(addCacheFallback(timeoutSet, bib, hostLms, ignoreCache)))
			.orElse(liveData);
	}

	private Mono<Item> memoizeLocationFromItem(Item item) {

		if ( item.getLocation() == null )
			return Mono.just(item);

		return Mono.just(item.getLocation())
			.flatMap(loc -> locationService.memoize(loc) )
			.thenReturn(item);
	}

	private Function<Mono<AvailabilityReport>, Mono<AvailabilityReport>> addCacheFallback(
		Duration timeout, BibRecord bib, HostLmsClient hostLms, boolean ignoreCache) {
		
		return (liveData) -> {
			// Return the livedata source or empty after timeout
			if (ignoreCache) { 
				return Mono.firstWithSignal(
					liveData,
					Mono.delay(timeout)
						.then(Mono.empty()));
			}
				
			
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
			.mapNotNull(availabilityCache::getIfPresent);
//			.map( this::modifyCachedRecord );
	}
	
//	private static final ItemStatus STATUS_UNKNOWN = new ItemStatus(ItemStatusCode.UNKNOWN);
	
//	private AvailabilityReport modifyCachedRecord(AvailabilityReport ar) {
//		// Build new item lists and set the status to unknown.
//		List<Item> newItems = new ArrayList<>();
//		ar.getItems().stream()
//			.map( Item::toBuilder )
//			.map( itemBuilder -> itemBuilder
//				.status(STATUS_UNKNOWN)
//				.build())
//			.forEach( newItems::add );
//		
//		return ar.toBuilder()
//			.items(newItems)
//			.build();
//	}
	
	private static AvailabilityReport getNoCachedValueErrorReport(String message) {
		return Optional.of(message)
			.map(msg -> AvailabilityReport.ofErrors(AvailabilityReport.Error.builder().message(msg).build()))
			.get();
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
