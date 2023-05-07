package org.olf.reshare.dcb.core.api;

import javax.validation.Valid;

import org.olf.reshare.dcb.core.model.ClusterRecord;
import org.olf.reshare.dcb.core.model.BibRecord;
import org.olf.reshare.dcb.storage.ClusterRecordRepository;
import org.olf.reshare.dcb.storage.BibRepository;
import java.util.UUID;
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
import java.time.Instant;
import java.util.List;
import java.util.Optional;

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

        @Secured(SecurityRule.IS_ANONYMOUS)
        @Operation(
                summary = "Browse Cluster Records",
                description = "Paginate through a Unified, Clustered view of the Bibliographic records",
                parameters = {
                        @Parameter(in = ParameterIn.QUERY, name = "number", description = "The page number", schema = @Schema(type = "integer", format = "int32"), example = "1"),
                        @Parameter(in = ParameterIn.QUERY, name = "size", description = "The page size", schema = @Schema(type = "integer", format = "int32"), example = "100"),
                        @Parameter(in = ParameterIn.QUERY, name = "since", description = "since", schema = @Schema(type = "string"))}
        )
        @Get("/{?pageable*,since}")
        public Mono<Page<ClusterRecordDTO>> listMapped(@Parameter(hidden = true) @Valid Pageable pageable, Optional<String> since) {

                if (pageable == null) {
                        pageable = Pageable.from(0, 100);
                }

		Instant i = null;
                if ( since.isPresent() ) {
                	log.debug("Since is present");
                	// Since is a timestamp formatted as 2023-05-03T18:56:40.872444Z
			i = Instant.parse(since.get());
			log.debug("Using instant: {}",i.toString());
                }
                else {
                	log.debug("Since is not present");
			i = java.time.Instant.ofEpochMilli(0L);
                }


                // return Mono.from( _clusterRecordRepository.findAll(pageable) )
                return Mono.from( _clusterRecordRepository.findByDateUpdatedGreaterThanOrderByDateUpdated(i,pageable) )
			.flatMap( page -> Mono.just(convertPage(page) )	); // Convert Page<ClusterRecord> into Page<ClusterDTO>
			
        }

	private Flux<ClusterRecordDTO> getFluxForPage(Page<ClusterRecordDTO> page) {
		return Flux.fromIterable(page);
	}

	private ClusterRecordDTO mapClusterRecordToDTO(ClusterRecord cr) {

		List<BibRecordDTO> bibs = new java.util.ArrayList();
		java.util.Set<BibRecord> bibs_from_db = cr.getBibs();
                if ( bibs_from_db != null ) {
			for ( BibRecord br : bibs_from_db ){
				bibs.add(mapBibToDTO(br));
			}
          	}

		// New cluster DTO
		return ClusterRecordDTO
				.builder()
				.dateUpdated(cr.getDateUpdated().toString())
				.dateCreated(cr.getDateCreated().toString())
				.clusterId(cr.getId())
				.title(cr.getTitle())
				.bibs(bibs)
				.build();
	}

	private BibRecordDTO mapBibToDTO(BibRecord br) {
		return BibRecordDTO
				.builder()
				.bibId(br.getId())
				.title(br.getTitle())
        			.sourceRecordId(br.getSourceRecordId())
        			.sourceSystemId(br.getSourceSystemId())
        			.sourceSystemCode(""+(br.getSourceSystemId()))
        			.recordStatus(br.getRecordStatus())
        			.typeOfRecord(br.getTypeOfRecord())
        			.derivedType(br.getDerivedType())
				.build();
	}

	private Page<ClusterRecordDTO> convertPage(Page<ClusterRecord> cr) {
		return cr.map(this::mapClusterRecordToDTO);
	}
	
        @Secured(SecurityRule.IS_ANONYMOUS)
        @Get("/{id}")
        public Mono<ClusterRecord> show(UUID id) {
		log.debug("ClusterRecordController::show({})",id);
                return Mono.from(_clusterRecordRepository.findById(id));
        }

}
