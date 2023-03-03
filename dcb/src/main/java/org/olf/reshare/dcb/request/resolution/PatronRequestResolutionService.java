package org.olf.reshare.dcb.request.resolution;

import java.util.UUID;

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
			.map(PatronRequestResolutionService::chooseFirstItem)
			.map(holdingsAndItemPair -> mapToSupplierRequest(holdingsAndItemPair,
				patronRequest));
	}

	private boolean validateClusteredBib(ClusteredBib clusteredBib) {
		log.debug(String.format("validateClusteredBib(%s)", clusteredBib));

		final var holdings = clusteredBib.holdings();

		if (holdings == null || holdings.isEmpty()) {
			throw new UnableToResolveHoldings("No holdings in clustered bib");
		}

		final var items = holdings.get(0).items();

		if (items == null || items.isEmpty()) {
			throw new UnableToResolveAnItem("No Items in holdings");
		}

		return true;
	}

	private static Holdings chooseFirstHoldings(ClusteredBib clusteredBib) {
		return clusteredBib.holdings().get(0);
	}

	private static HoldingsAndItemPair chooseFirstItem(Holdings holdings) {
		return new HoldingsAndItemPair(holdings, holdings.items().get(0));
	}

	private static SupplierRequest mapToSupplierRequest(
		HoldingsAndItemPair holdingsAndItemPair, PatronRequest patronRequest) {

		Holdings.Agency agency = holdingsAndItemPair.holdings.agency();
		log.debug(String.format("mapToSupplierRequest(%s, %s)",
			holdingsAndItemPair.item,  agency));

		final var uuid = UUID.randomUUID();
		log.debug(String.format("create sr %s %s %s", uuid,
			holdingsAndItemPair.item, agency));

		return new SupplierRequest(uuid, patronRequest,
			holdingsAndItemPair.item.id(), agency.code());
	}

	private record HoldingsAndItemPair(Holdings holdings, Holdings.Item item) { }
}
