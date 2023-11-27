package org.olf.dcb.item.availability;

import static io.micronaut.core.util.CollectionUtils.isEmpty;
import static org.olf.dcb.item.availability.AvailabilityReport.emptyReport;

import java.util.UUID;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.request.resolution.Bib;
import org.olf.dcb.request.resolution.ClusteredBib;
import org.olf.dcb.request.resolution.NoBibsForClusterRecordException;
import org.olf.dcb.request.resolution.SharedIndexService;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Prototype
public class LiveAvailabilityService {
	private final HostLmsService hostLmsService;
	private final RequestableItemService requestableItemService;
	private final SharedIndexService sharedIndexService;

	public LiveAvailabilityService(HostLmsService hostLmsService,
		RequestableItemService requestableItemService, SharedIndexService sharedIndexService) {

		this.hostLmsService = hostLmsService;
		this.requestableItemService = requestableItemService;
		this.sharedIndexService = sharedIndexService;
	}

	public Mono<AvailabilityReport> getAvailableItems(UUID clusteredBibId) {
		log.debug("getAvailableItems({})", clusteredBibId);

		return sharedIndexService.findClusteredBib(clusteredBibId)
			.flatMap(clusteredBib -> getBibs(clusteredBib)
			.flatMap(this::getItems)
			.map(this::determineRequestability)
			.reduce(emptyReport(), AvailabilityReport::combineReports)
			.map(AvailabilityReport::sortItems)
			.switchIfEmpty(Mono.error(new RuntimeException("Failed to resolve items for cluster record " + clusteredBib))));
	}

	private Flux<Bib> getBibs(ClusteredBib clusteredBib) {
		log.debug("getBibs: {}", clusteredBib);

		final var bibs = clusteredBib.getBibs();

		if (isEmpty(bibs)) {
			log.error("Clustered bib record: \"" + clusteredBib.getId() + "\" has no bibs");

			return Flux.error(new NoBibsForClusterRecordException(clusteredBib.getId()));
		}

		return Flux.fromIterable(bibs);
	}

	private Mono<AvailabilityReport> getItems(Bib bib) {
		log.debug("getItems({})", bib);

		return hostLmsService.getClientFor(bib.getHostLms())
			.flatMap(hostLmsClient -> hostLmsClient
				.getItems(bib.getSourceRecordId()))
			.doOnError(error -> log.error("Error occurred fetching items: ", error))
			.map(AvailabilityReport::ofItems)
			.onErrorReturn(AvailabilityReport.ofErrors(mapToError(bib)));
	}

	private AvailabilityReport determineRequestability(AvailabilityReport report) {
		return report.forEachItem(
			item -> item.setIsRequestable(requestableItemService.isRequestable(item)));
	}

	private static AvailabilityReport.Error mapToError(Bib bib) {
		log.error("Failed to fetch items for bib: {} from host: {}",
			bib.getSourceRecordId(), bib.getHostLms().getCode());

		return AvailabilityReport.Error.builder()
			.message(String.format("Failed to fetch items for bib: %s from host: %s",
				bib.getSourceRecordId(), bib.getHostLms().getCode()))
			.build();
	}
}
