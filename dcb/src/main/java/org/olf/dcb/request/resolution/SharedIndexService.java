package org.olf.dcb.request.resolution;

import static org.olf.dcb.utils.PublisherErrors.failWhenEmpty;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.clustering.ClusterRecord;
import org.olf.dcb.storage.BibRepository;
import org.olf.dcb.storage.ClusterRecordRepository;
import org.reactivestreams.Publisher;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Prototype
public class SharedIndexService {
	private final ClusterRecordRepository clusterRecordRepository;
	private final BibRepository bibRepository;
	private final HostLmsService hostLmsService;

	public SharedIndexService(ClusterRecordRepository clusterRecordRepository,
		BibRepository bibRepository, HostLmsService hostLmsService) {

		this.clusterRecordRepository = clusterRecordRepository;
		this.bibRepository = bibRepository;
		this.hostLmsService = hostLmsService;
	}

	public Mono<ClusteredBib> findClusteredBib(UUID bibClusterId) {
		log.debug("findClusteredBib({})", bibClusterId);

		// Repository returns null when cluster record cannot be found
		return Mono.from(clusterRecordRepository.findOneById(bibClusterId))
			.map(Optional::ofNullable)
			.defaultIfEmpty(Optional.empty())
			.map(optionalClusterRecord -> failWhenEmpty(optionalClusterRecord,
				() -> new CannotFindClusterRecordException(bibClusterId)))
			.zipWhen(this::findBibRecords, this::mapToClusteredBibWithBib);
	}

	private ClusteredBib mapToClusteredBibWithBib(ClusterRecord clusterRecord,
		List<Bib> bibs) {
		return ClusteredBib.builder()
			.id(clusterRecord.getId())
			.title(clusterRecord.getTitle())
			.bibs(bibs)
			.build();
	}

	private Mono<List<Bib>> findBibRecords(ClusterRecord clusteredBib) {
		return Flux.from(bibRepository.findAllByContributesTo(clusteredBib))
			.flatMap(this::findHostLms)
			.collectList();
	}

	private Publisher<Bib> findHostLms(BibRecord bibRecord) {
		return Mono.from(hostLmsService.findById(bibRecord.getSourceSystemId()))
			.map(hostLms -> Bib.builder()
				.id(bibRecord.getId())
				.sourceRecordId(bibRecord.getSourceRecordId())
				.hostLms(hostLms)
				.build());
	}
}
