package org.olf.reshare.dcb.request.resolution;

import java.util.UUID;
import java.util.function.BiFunction;

import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.core.model.SupplierRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
public class PatronRequestResolutionService {
	private static final Logger log = LoggerFactory.getLogger(PatronRequestResolutionService.class);

	private final SharedIndexService sharedIndexService;

	public PatronRequestResolutionService(SharedIndexService sharedIndexService) {
		this.sharedIndexService = sharedIndexService;
	}

	public Mono<SupplierRequest> resolvePatronRequest(PatronRequest patronRequest) {
		log.debug(String.format("resolvePatronRequest(%s)", patronRequest));

		return sharedIndexService.findClusteredBib(patronRequest.getBibClusterId())
			.filter(this::validateClusteredBib)
			.map(PatronRequestResolutionService::chooseFirstHoldings)
			.zipWhen(PatronRequestResolutionService::chooseFirstItem,
				mapToSupplierRequest(patronRequest));
	}

	private boolean validateClusteredBib(ClusteredBib clusteredBib) {
		log.debug(String.format("validateClusteredBib(%s)", clusteredBib));

		final var holdings = clusteredBib.getHoldings();

		if (holdings == null || holdings.isEmpty()) {
			throw new UnableToResolveHoldings("No holdings in clustered bib");
		}

		final var items = holdings.get(0).getItems();

		if (items == null || items.isEmpty()) {
			throw new UnableToResolveAnItem("No Items in holdings");
		}

		return true;
	}

	private static Holdings chooseFirstHoldings(ClusteredBib clusteredBib) {
		return clusteredBib.getHoldings().get(0);
	}

	private static Mono<Item> chooseFirstItem(Holdings holdings) {
		return Mono.just(holdings.getItems().get(0));
	}

	private static BiFunction<Holdings, Item, SupplierRequest> mapToSupplierRequest(
		PatronRequest patronRequest) {

		return (holdings, item) -> mapToSupplierRequest(holdings, item, patronRequest);
	}

	private static SupplierRequest mapToSupplierRequest(Holdings holdings,
		Item item, PatronRequest patronRequest) {

		Agency agency = holdings.getAgency();

		log.debug(String.format("mapToSupplierRequest(%s, %s)",
			item,  agency));

		final var uuid = UUID.randomUUID();
		log.debug(String.format("create sr %s %s %s", uuid,
			item, agency));

		log.debug("Resolve the patron request");
		final var updatedPatronRequest = patronRequest.resolve();

		return new SupplierRequest(uuid, updatedPatronRequest,
			item.getId(), agency.getCode());
	}
}
