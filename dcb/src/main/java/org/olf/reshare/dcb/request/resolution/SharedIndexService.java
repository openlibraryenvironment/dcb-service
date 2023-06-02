package org.olf.reshare.dcb.request.resolution;

import io.micronaut.context.annotation.Prototype;
import org.olf.reshare.dcb.core.HostLmsService;
import org.olf.reshare.dcb.core.model.BibRecord;
import org.olf.reshare.dcb.core.model.clustering.ClusterRecord;
import org.olf.reshare.dcb.storage.BibRepository;
import org.olf.reshare.dcb.storage.ClusterRecordRepository;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.olf.reshare.dcb.utils.PublisherErrors.failWhenEmpty;

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

	public Mono<Object> getCanonicalMetadataByBibClusterId(UUID bibClusterId, String key) {
		log.debug("{{getCanonicalMetadataByBibClusterId}}: {}", bibClusterId);

		return Mono.from(clusterRecordRepository.findOneById(bibClusterId))
			.flatMap(clusterRecord -> Mono.from(bibRepository.findById(clusterRecord.getSelectedBib())))
			.map(bibRecord -> bibRecord.getCanonicalMetadata().get(key))
			.switchIfEmpty(Mono.error(() -> new CannotFindClusterRecordException(bibClusterId)));
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
			.map(hostLms -> new Bib(bibRecord.getId(), bibRecord.getSourceRecordId(),
				hostLms));
	}
}
