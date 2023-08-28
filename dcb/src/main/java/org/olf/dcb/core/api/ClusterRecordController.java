package org.olf.dcb.core.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.validation.Valid;

import org.olf.dcb.core.api.types.BibRecordDTO;
import org.olf.dcb.core.api.types.ClusterBibDTO;
import org.olf.dcb.core.api.types.ClusterRecordDTO;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.clustering.ClusterRecord;
import org.olf.dcb.storage.BibRepository;
import org.olf.dcb.storage.ClusterRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.sql.ResultSet;
import java.sql.SQLException;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.micronaut.core.convert.ConversionService;

@Controller("/clusters")
@Tag(name = "Cluster Records (Read only)")
public class ClusterRecordController {

	private static final Logger log = LoggerFactory.getLogger(ClusterRecordController.class);


	private final ClusterRecordRepository _clusterRecordRepository;
	private final BibRepository _bibRepository;
	private final ConversionService conversionService;

	public ClusterRecordController(ClusterRecordRepository clusterRecordRepository,
                BibRepository bibRepository,
                ConversionService conversionService) {
		_clusterRecordRepository = clusterRecordRepository;
		_bibRepository = bibRepository;
                this.conversionService = conversionService;
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
		if (since.isPresent()) {
			// log.debug("Since is present");
			// Since is a timestamp formatted as 2023-05-03T18:56:40.872444Z
			i = Instant.parse(since.get());
			// log.debug("Using instant: {}", i.toString());
		} else {
			// log.debug("Since is not present");
			i = java.time.Instant.ofEpochMilli(0L);
		}


		// return Mono.from( _clusterRecordRepository.findAll(pageable) )
		return Mono.from(_clusterRecordRepository.findByDateUpdatedGreaterThanOrderByDateUpdated(i, pageable))
			.flatMap(page -> Mono.just(convertPage(page)))
			.flatMap(pageOfClusterDTO -> {
				List<ClusterRecordDTO> clusterRecords = pageOfClusterDTO.getContent();
				return Flux.fromIterable(clusterRecords)
					.flatMap(clusterRecord -> {
						// go fetch the bib record here and attach it if we find one	
						return Mono.from(_bibRepository.findById(clusterRecord.getSelectedBibId()))
							.flatMap(bib -> {
								if (bib != null) {
									clusterRecord.setSelectedBib(mapBibToDTO(bib));
								}
								return Mono.just(clusterRecord);
							});
					})
					.flatMap(clusterRecord -> {
						// Add in the IDs of the bib records that compose this cluster - so we can find cluster records
						// by the id of their members
						return getBibsForCluster(clusterRecord.getClusterId())
							.flatMap(bibs -> {
								clusterRecord.setBibs(bibs);
								return Mono.just(clusterRecord);
							});
					})
					.collectList()
					.map(enrichedClusterRecords -> {
						return Page.of(enrichedClusterRecords, pageOfClusterDTO.getPageable(), pageOfClusterDTO.getTotalSize());
					});
			});

	}

        private ClusterBibDTO clusterBibFromMemberBib(BibRepository.MemberBib bibProjection) {
                return ClusterBibDTO
                           .builder()
                           .bibId(bibProjection.bibid())
                           .title(bibProjection.title())
                           .sourceRecordId(bibProjection.sourcerecordid())
                           .sourceSystem(bibProjection.sourcesystem())
                           .metadataScore(bibProjection.metadatascore())
                           .clusterReason(bibProjection.clusterreason())
                           .build();
        }

	private Mono<List<ClusterBibDTO>> getBibsForCluster(UUID cluster_id) {
                // b.id, b.title, b.sourceRecordId, b.metadataScore, b.cluster_reason, h.code
		return Flux.from(_bibRepository.findMemberBibsForCluster(cluster_id))
                        .map( bibProjection -> clusterBibFromMemberBib(bibProjection) )
			.collectList();
	}

//	private Flux<ClusterRecordDTO> getFluxForPage(Page<ClusterRecordDTO> page) {
//		return Flux.fromIterable(page);
//	}

	private ClusterRecordDTO mapClusterRecordToDTO(ClusterRecord cr) {

		// Mono<BibRecord> br = Mono.from(_bibRepository.findById(cr.getSelectedBib()));
		// BibRecordDTO selectedBib = mapBibToDTO(br.block());

		// New cluster DTO
		return ClusterRecordDTO
			.builder()
			.dateUpdated(cr.getDateUpdated().toString())
			.dateCreated(cr.getDateCreated().toString())
			.clusterId(cr.getId())
			.title(cr.getTitle())
			.selectedBibId(cr.getSelectedBib())
			.build();
	}

	private BibRecordDTO mapBibToDTO(BibRecord br) {
		return BibRecordDTO
			.builder()
			.bibId(br.getId())
			.title(br.getTitle(conversionService))
			.sourceRecordId(br.getSourceRecordId())
			.sourceSystemId(br.getSourceSystemId())
			.sourceSystemCode(String.valueOf(br.getSourceSystemId()))
			.recordStatus(br.getRecordStatus(conversionService))
			.typeOfRecord(br.getTypeOfRecord())
			.derivedType(br.getDerivedType(conversionService))
			.canonicalMetadata(br.getCanonicalMetadata())
			.build();
	}

	private Page<ClusterRecordDTO> convertPage(Page<ClusterRecord> cr) {
		return cr.map(this::mapClusterRecordToDTO);
	}

	@Secured(SecurityRule.IS_ANONYMOUS)
	@Get("/{id}")
	public Mono<ClusterRecord> show(UUID id) {
		log.debug("ClusterRecordController::show({})", id);
		return Mono.from(_clusterRecordRepository.findById(id));
	}

}
