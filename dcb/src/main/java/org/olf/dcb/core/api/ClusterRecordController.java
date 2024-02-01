package org.olf.dcb.core.api;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.olf.dcb.core.api.serde.ClusterRecordDTO;
import org.olf.dcb.core.model.clustering.ClusterRecord;
import org.olf.dcb.core.svc.RecordClusteringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.transaction.annotation.Transactional;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import reactor.core.publisher.Mono;


@Transactional(readOnly = true)
@Controller("/clusters")
@Tag(name = "Cluster Records (Read only)")
public class ClusterRecordController {

	private static final Logger log = LoggerFactory.getLogger(ClusterRecordController.class);

	private final RecordClusteringService recordClusteringService;
	
	public ClusterRecordController(
			RecordClusteringService recordClusteringService) {
		this.recordClusteringService = recordClusteringService;
	}

	@Secured(SecurityRule.IS_ANONYMOUS)
	@Operation(
		summary = "Browse Cluster Records",
		description = "Paginate through a Unified, Clustered view of the Bibliographic records",
		parameters = {
			@Parameter(in = ParameterIn.QUERY, name = "number", description = "The page number", schema = @Schema(type = "integer", format = "int32"), example = "1"),
			@Parameter(in = ParameterIn.QUERY, name = "size", description = "The page size", schema = @Schema(type = "integer", format = "int32"), example = "100"),
			@Parameter(in = ParameterIn.QUERY, name = "since", description = "since", schema = @Schema(type = "string"))}
	)
	@Get("/{?since}{?pageable*}")
	public Mono<Page<ClusterRecordDTO>> listMapped(Optional<Instant> since, @Parameter(hidden = true) @Valid Pageable pageable) {

		// return Mono.from( _clusterRecordRepository.findAll(pageable) )
		return recordClusteringService.getPageAs(since, pageable, ClusterRecordDTO::new);
	}

	@Secured(SecurityRule.IS_ANONYMOUS)
	@Get("/{id}")
	public Mono<ClusterRecord> show(UUID id) {
		log.debug("ClusterRecordController::show({})", id);
		return Mono.from(recordClusteringService.findById(id));
	}

}
