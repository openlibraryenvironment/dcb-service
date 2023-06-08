package org.olf.reshare.dcb.item.availability;

import static org.olf.reshare.dcb.item.availability.AvailabilityReport.emptyReport;

import org.olf.reshare.dcb.core.HostLmsService;
import org.olf.reshare.dcb.request.resolution.Bib;
import org.olf.reshare.dcb.request.resolution.ClusteredBib;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Prototype;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Prototype
public class LiveAvailabilityService {
	private static final Logger log = LoggerFactory.getLogger(LiveAvailabilityService.class);

	private final HostLmsService hostLmsService;
	private final RequestableItemService requestableItemService;

	public LiveAvailabilityService(HostLmsService hostLmsService,
		RequestableItemService requestableItemService) {

		this.hostLmsService = hostLmsService;
		this.requestableItemService = requestableItemService;
	}

	public Mono<AvailabilityReport> getAvailableItems(ClusteredBib clusteredBib) {
		log.debug("getAvailableItems({})", clusteredBib);

		return getBibs(clusteredBib)
			.flatMap(this::getItems)
			.map(this::determineRequestability)
			.reduce(emptyReport(), AvailabilityReport::combineReports)
			.map(AvailabilityReport::sortItems);
	}

	private Flux<Bib> getBibs(ClusteredBib clusteredBib) {
		log.debug("getBibs: {}", clusteredBib);

		final var bibs = clusteredBib.getBibs();

		if (bibs== null) {
			log.error("Bibs cannot be null when asking for available items");

			return Flux.error(new IllegalArgumentException("Bibs cannot be null"));
		}

		return Flux.fromIterable(bibs);
	}

	private Mono<AvailabilityReport> getItems(Bib bib) {
		log.debug("getItems({})", bib);

		if (bib.getHostLms() == null) {
			log.error("hostLMS cannot be null when asking for available items");

			return Mono.error(new IllegalArgumentException("hostLMS cannot be null"));

		} else {
			return hostLmsService.getClientFor(bib.getHostLms())
				.flatMap(hostLmsClient -> hostLmsClient
					.getItemsByBibId(bib.getBibRecordId(), bib.getHostLms().getCode()))
				.doOnError(error -> log.debug("Error occurred fetching items: ", error))
				.map(AvailabilityReport::ofItems)
				.onErrorReturn(AvailabilityReport.ofErrors(mapToError(bib)));
		}
	}

	private AvailabilityReport determineRequestability(AvailabilityReport report) {
		return report.forEachItem(
			item -> item.setIsRequestable(requestableItemService.isRequestable(item)));
	}

	private static AvailabilityReport.Error mapToError(Bib bib) {
		return AvailabilityReport.Error.builder().message(String.format(
			"Failed to fetch items for bib: %s from host: %s",
			bib.getBibRecordId(), bib.getHostLms().getCode())).build();
	}
}
