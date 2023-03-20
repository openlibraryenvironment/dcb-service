package org.olf.reshare.dcb.core.api;

import javax.validation.Valid;

import org.olf.reshare.dcb.core.model.ClusterRecord;
import org.olf.reshare.dcb.storage.ClusterRecordRepository;

import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Mono;

@Controller("/clusters")
@Tag(name = "Cluster Records (Read only)")
public class ClusterRecordController {

	private final ClusterRecordRepository _clusterRecordRepository;

	public ClusterRecordController(ClusterRecordRepository clusterRecordRepository) {
		_clusterRecordRepository = clusterRecordRepository;
	}

	@Secured(SecurityRule.IS_ANONYMOUS)
	@Operation(
		summary = "Browse Cluster Records",
		description = "Paginate through a Unified, Clustered view of the Bibliographic records",
		parameters = {
			@Parameter(in = ParameterIn.QUERY, name = "number", description = "The page number", schema = @Schema(type = "integer", format = "int32"), example = "1"),
			@Parameter(in = ParameterIn.QUERY, name = "size", description = "The page size", schema = @Schema(type = "integer", format = "int32"), example = "100")}
	)
	@Get("/{?pageable*}")
	public Mono<Page<ClusterRecord>> list(@Parameter(hidden = true) @Valid Pageable pageable) {
		if (pageable == null) {
			pageable = Pageable.from(0, 100);
		}

		return Mono.from(_clusterRecordRepository.findAll(pageable));
	}
}
