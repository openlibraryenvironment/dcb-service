package org.olf.dcb.core.api;

import java.util.UUID;

import org.olf.dcb.core.clustering.RecordClusteringService;
import org.olf.dcb.core.clustering.model.MatchPoint;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.svc.BibRecordService;
import org.olf.dcb.security.RoleNames;

import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.transaction.annotation.Transactional;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Controller("/bibs")
@Validated
@Secured(RoleNames.ADMINISTRATOR)
@Tag(name = "Bib API")
public class BibController {

	private final BibRecordService bibService;
	private final RecordClusteringService recordClusteringService;

	public BibController(BibRecordService bibService, RecordClusteringService recordClusteringService) {
		this.bibService = bibService;
		this.recordClusteringService = recordClusteringService;
	}

	@Secured(SecurityRule.IS_ANONYMOUS)
	@Operation(
		summary = "Browse Bibs",
		description = "Paginate through the list of known Bibilographic entries",
		parameters = {
			@Parameter(in = ParameterIn.QUERY, name = "number", description = "The page number", schema = @Schema(type = "integer", format = "int32"), example = "1"),
			@Parameter(in = ParameterIn.QUERY, name = "size", description = "The page size", schema = @Schema(type = "integer", format = "int32"), example = "100")}
	)
	@Get("/{?pageable*}")
	public Mono<Page<BibRecord>> list(@Parameter(hidden = true) @Valid Pageable pageable) {
		if (pageable == null) {
			pageable = Pageable.from(0, 100);
		}
		return bibService.getPageOfBibs(pageable);
	}

	@Get("/{id}")
	public Mono<BibRecord> show(UUID id) {
		return bibService.getById(id);
	}

	@Transactional( readOnly = true )
	@Get("/{id}/matchpoints")
	public Flux<? super MatchPoint> matchPoints( UUID id ) {
		return bibService.getById(id)
			.flatMapMany(recordClusteringService::generateMatchPoints);
	}

}
