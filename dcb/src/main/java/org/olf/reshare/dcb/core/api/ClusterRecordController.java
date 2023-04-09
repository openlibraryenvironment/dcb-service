package org.olf.reshare.dcb.core.api;

import javax.validation.Valid;

import org.olf.reshare.dcb.core.model.ClusterRecord;
import org.olf.reshare.dcb.core.model.BibRecord;
import org.olf.reshare.dcb.storage.ClusterRecordRepository;
import org.olf.reshare.dcb.storage.BibRepository;

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
import reactor.core.publisher.Flux;
import org.olf.reshare.dcb.core.api.types.ClusterRecordDTO;
import org.olf.reshare.dcb.core.api.types.BibRecordDTO;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Controller("/clusters")
@Tag(name = "Cluster Records (Read only)")
public class ClusterRecordController {

        private static final Logger log = LoggerFactory.getLogger(ClusterRecordController.class);


	private final ClusterRecordRepository _clusterRecordRepository;
	private final BibRepository _bibRepository;

	public ClusterRecordController(ClusterRecordRepository clusterRecordRepository,
					BibRepository bibRepository) {
		_clusterRecordRepository = clusterRecordRepository;
		_bibRepository = bibRepository;
	}

	/*
	@Secured(SecurityRule.IS_ANONYMOUS)
	@Operation(
		summary = "Browse Cluster Records",
		description = "Paginate through a Unified, Clustered view of the Bibliographic records",
		parameters = {
			@Parameter(in = ParameterIn.QUERY, name = "number", description = "The page number", schema = @Schema(type = "integer", format = "int32"), example = "1"),
			@Parameter(in = ParameterIn.QUERY, name = "size", description = "The page size", schema = @Schema(type = "integer", format = "int32"), example = "100")}
	)
	@Get("/legacy{?pageable*}")
	public Mono<Page<ClusterRecord>> list(@Parameter(hidden = true) @Valid Pageable pageable) {
		if (pageable == null) {
			pageable = Pageable.from(0, 100);
		}

		return Mono.from(_clusterRecordRepository.findAll(pageable));
	}
	*/

        @Secured(SecurityRule.IS_ANONYMOUS)
        @Operation(
                summary = "Browse Cluster Records",
                description = "Paginate through a Unified, Clustered view of the Bibliographic records",
                parameters = {
                        @Parameter(in = ParameterIn.QUERY, name = "number", description = "The page number", schema = @Schema(type = "integer", format = "int32"), example = "1"),
                        @Parameter(in = ParameterIn.QUERY, name = "size", description = "The page size", schema = @Schema(type = "integer", format = "int32"), example = "100")}
        )
        @Get("/{?pageable*}")
        public Mono<Page<ClusterRecordDTO>> listMapped(@Parameter(hidden = true) @Valid Pageable pageable) {

                if (pageable == null) {
                        pageable = Pageable.from(0, 100);
                }

                return Mono.from( _clusterRecordRepository.findAll(pageable) )
			.flatMap( page -> Mono.just(convertPage(page) )	); // Convert Page<ClusterRecord> into Page<ClusterDTO>
			
        }

	private Flux<ClusterRecordDTO> getFluxForPage(Page<ClusterRecordDTO> page) {
		return Flux.fromIterable(page);
	}

	private ClusterRecordDTO enrichClusterRecordWithBibs(ClusterRecordDTO input) {
		input.setTitle("ENRICHED: "+input.getTitle());
		return input;
	}

	private ClusterRecordDTO mapClusterRecordToDTO(ClusterRecord cr) {

		// New cluster DTO
		return ClusterRecordDTO
				.builder()
				.clusterId(cr.getId())
				.title("MAPPED"+cr.getTitle())
				.build();
	}

	private BibRecordDTO mapBibToDTO(BibRecord br) {
		return BibRecordDTO
				.builder()
				.bibId(br.getId())
				.title(br.getTitle())
				.build();
	}

	private Page<ClusterRecordDTO> convertPage(Page<ClusterRecord> cr) {
		return cr.map(this::mapClusterRecordToDTO);
	}
	
}
