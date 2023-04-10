package org.olf.reshare.dcb.request.resolution;

import static org.olf.reshare.dcb.utils.PublisherErrors.failWhenEmpty;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.olf.reshare.dcb.core.HostLmsService;
import org.olf.reshare.dcb.core.model.BibRecord;
import org.olf.reshare.dcb.core.model.ClusterRecord;
import org.olf.reshare.dcb.storage.BibRepository;
import org.olf.reshare.dcb.storage.ClusterRecordRepository;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Prototype;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Prototype
public class SharedIndexService implements ClusteredBibFinder {
	private static final Logger log = LoggerFactory.getLogger(SharedIndexService.class);

	private final ClusterRecordRepository clusterRecordRepository;
	private final BibRepository bibRepository;
	private final HostLmsService hostLmsService;

	public SharedIndexService(ClusterRecordRepository clusterRecordRepository,
		BibRepository bibRepository, HostLmsService hostLmsService) {

		this.clusterRecordRepository = clusterRecordRepository;
		this.bibRepository = bibRepository;
		this.hostLmsService = hostLmsService;
	}

	@Override
	public Mono<ClusteredBib> findClusteredBib(UUID bibClusterId) {
		log.debug("{{findClusteredBib}}: {}", bibClusterId);

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
		return new ClusteredBib()
				.builder()
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
			.map(hostLms -> new Bib(bibRecord.getId(), bibRecord.getSourceRecordId(),
				hostLms));
	}
}
