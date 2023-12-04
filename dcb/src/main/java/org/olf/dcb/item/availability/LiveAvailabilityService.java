package org.olf.dcb.item.availability;

import static org.olf.dcb.item.availability.AvailabilityReport.emptyReport;

import java.util.UUID;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.model.BibRecord;
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
			.flatMapIterable(ClusteredBib::getBibs)
			.switchIfEmpty(Flux.error(new NoBibsForClusterRecordException(clusteredBibId)))
			.flatMap(this::getItems)
			.map(this::determineRequestability)
			.reduce(emptyReport(), AvailabilityReport::combineReports)
			.map(AvailabilityReport::sortItems)
			.switchIfEmpty(Mono.error(new RuntimeException("Failed to resolve items for cluster record " + clusteredBibId)));
	}

	private Mono<AvailabilityReport> getItems(BibRecord bib) {
		log.debug("getItems({})", bib);

		return hostLmsService.getClientFor(bib.getSourceSystemId())
			.flatMap(client -> getItems(bib, client));
	}

	private Mono<AvailabilityReport> getItems(BibRecord bib, HostLmsClient client) {
		return client.getItems(bib)
			.doOnError(error -> log.error("Error occurred fetching items: ", error))
			.map(AvailabilityReport::ofItems)
			.onErrorReturn(AvailabilityReport.ofErrors(mapToError(bib, client.getHostLmsCode())));
	}

	private AvailabilityReport determineRequestability(AvailabilityReport report) {
		return report.forEachItem(
			item -> item.setIsRequestable(requestableItemService.isRequestable(item)));
	}

	private static AvailabilityReport.Error mapToError(BibRecord bib, String hostLmsCode) {
		log.error("Failed to fetch items for bib: {} from host: {}",
			bib.getSourceRecordId(), hostLmsCode);

		return AvailabilityReport.Error.builder()
			.message(String.format("Failed to fetch items for bib: %s from host: %s",
				bib.getSourceRecordId(), hostLmsCode))
			.build();
	}
}
