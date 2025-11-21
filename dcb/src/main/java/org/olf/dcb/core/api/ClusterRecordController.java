package org.olf.dcb.core.api;

import static org.olf.dcb.security.RoleNames.ADMINISTRATOR;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.olf.dcb.core.api.serde.ClusterRecordDTO;
import org.olf.dcb.core.clustering.RecordClusteringService;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.clustering.model.ClusterRecord;
import org.olf.dcb.core.clustering.model.MatchPoint;
import org.olf.dcb.storage.BibRepository;
import org.olf.dcb.storage.ClusterRecordRepository;
import org.olf.dcb.storage.MatchPointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.transaction.annotation.Transactional;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.Data;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Controller("/clusters")
@Validated
@Secured(SecurityRule.IS_ANONYMOUS)
@Tag(name = "Cluster Records (Read only)")
@Transactional(readOnly = true)
public class ClusterRecordController {

	private static final Logger log = LoggerFactory.getLogger(ClusterRecordController.class);

	private final BibRepository bibRepository;
	private final ClusterRecordRepository clusterRecordRepository;
	private final MatchPointRepository matchPointRepository;
	private final ObjectMapper objectMapper;
	private final RecordClusteringService recordClusteringService;
	
	public ClusterRecordController(
			BibRepository bibRepository,
			ClusterRecordRepository clusterRecordRepository,
			MatchPointRepository matchPointRepository,
			ObjectMapper objectMapper,
			RecordClusteringService recordClusteringService
		) {
		this.bibRepository = bibRepository;
		this.clusterRecordRepository = clusterRecordRepository;
		this.matchPointRepository = matchPointRepository;
		this.objectMapper = objectMapper;
		this.recordClusteringService = recordClusteringService;
	}

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

	@Get("/{id}")
	public Mono<ClusterRecord> show(UUID id) {
		log.debug("ClusterRecordController::show({})", id);
		return Mono.from(recordClusteringService.findById(id));
	}

	@Secured(ADMINISTRATOR)
	@Get("/{id}/exportMembers")
	public Mono<HttpResponse<byte[]>> exportMembers(UUID id) throws IOException {

		return Flux.from(bibRepository.findAllByContributesToId(id))
			.collectList()
			.flatMap( records -> {
				
				if (records.isEmpty()) {
					return Mono.just(HttpResponse.noContent());
				}

				return Mono.fromCallable(() -> {
					try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                         ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream)) {
	
						// Iterate through records and add each as a JSON file to the ZIP
						for (BibRecord record : records) {
							String json = objectMapper.writeValueAsString(record.getCanonicalMetadata());
							String fileName = "record-" + record.getId() + ".json"; // Assuming `getId` exists
	
							ZipEntry zipEntry = new ZipEntry(fileName);
							zipOutputStream.putNextEntry(zipEntry);
							zipOutputStream.write(json.getBytes());
							zipOutputStream.closeEntry();
						}

						zipOutputStream.finish();
						return HttpResponse.ok(byteArrayOutputStream.toByteArray())
								.header("Content-Disposition", "attachment; filename=\"cluster-"+id+"records.zip\"");
					}
				})
				.onErrorResume( e -> {
					return Mono.just(HttpResponse.serverError()
						.body(("Error: " + e.getMessage()).getBytes()));
				});
			});
	}
	
	@Secured(ADMINISTRATOR)
	@Post("/{id}/reprocess")
	public Mono<HttpResponse<String>> reprocess(@NonNull final UUID id) {
		return Mono.<String>create(report -> {
      log.info("Starting Cluster reprocess");
      report.success("Reprocess cluster [%s] started at [%s]".formatted(id, Instant.now()));
      
      recordClusteringService.disperseAndRecluster(id)
	      .doOnTerminate(() -> {
	        log.info("Finished reprocess cluster [{}]", id);
	      })
	      .subscribe();
      
		}).map(HttpResponse.accepted()::<String>body);
	}

	@Data
	@Serdeable
	private class ResultMatchPointBib {
		UUID id;
		String title;
		String hostName;
		Integer processVersion;
		Integer numberOfMatchPoints;
		public String toString() {
			return("Id: " + id.toString() + "\nHostName: " + hostName + "\nProcessVersion: " + processVersion + "\nNumberOfMatchPoints: " + numberOfMatchPoints);
		}
	};

	@Data
	@Serdeable
	private class ResultMatchPoint {
		UUID matchPointValue;
		String namespace;
		String value;
		String domain;
		List<ResultMatchPointBib> bibs = new ArrayList<ResultMatchPointBib>();
		public String toString() {
			return("MatchPointValue: " + matchPointValue.toString() + "\nNamespace: " + namespace + "\nValue: " + value + "\nDomain: " + domain + " \nBibs: " + bibs.toString());
		}
	};

	@Data
	@Serdeable
	private class ResultMatchPointCluster {
		UUID clusterId;
		String title;
		List<ResultMatchPoint> matchPoints = new ArrayList<ResultMatchPoint>();
		public String toString() {
			return("ClusterId: " + clusterId.toString() + "\nTitle: " + title + "\nMatchPoints: " +matchPoints.toString());
		}
	}

	// TODO: These have been copied, see the comment in the area that is using them as we do not want copied values here
	private static final String MATCHPOINT_ID = "id";
	private static final List<String> namesspacesUsedForClustering = List.of("BLOCKING_TITLE","GOLDRUSH","GOLDRUSH::TITLE","ONLY-ISBN-13", "ISSN-N", "LCCN", "OCOLC", "STRN" );
	
	@Operation(
		summary = "Obtain match point details for a cluster",
		description = "Supplies the details about match points for all bibs within a cluster",
		parameters = {
			@Parameter(in = ParameterIn.PATH, name = "id", description = "The cluster id you want the details for", schema = @Schema(type = "string"), example = "00063f43-1d1f-48e3-84de-eed4b1f6a042")
		}
	)
	@Get("/{id}/matchPointDetails")
	public Mono<ResultMatchPointCluster> matchPointDetails(UUID id) throws IOException {

		ResultMatchPointCluster resultMatchPointCluster = new ResultMatchPointCluster();
		resultMatchPointCluster.clusterId = id;
		Map<UUID, ResultMatchPoint> matchPointMap = new HashMap<UUID, ResultMatchPoint>();

		return(
			Mono.from(clusterRecordRepository.findById(id))
				.map(clusterRecord -> {
					resultMatchPointCluster.setTitle(clusterRecord.getTitle());
					return(resultMatchPointCluster);
				})
				.flatMapMany( a -> Flux.from(bibRepository.findMatchPointDetailsFor(id)) )
				.collectList()
				.map( bibMatchPointDetails -> {
					// Loop through each of the match points
					for (BibRepository.BibMatchPointDetail bibMatchPointDetail : bibMatchPointDetails) {
						// Do we have it in our map
						ResultMatchPoint resultMatchPoint = matchPointMap.get(bibMatchPointDetail.matchPointValue());
						if (resultMatchPoint == null) {
							// We do not so create a new one
							resultMatchPoint = new ResultMatchPoint();
							resultMatchPoint.setDomain(bibMatchPointDetail.matchPointDomain());
							resultMatchPoint.setMatchPointValue(bibMatchPointDetail.matchPointValue());
							matchPointMap.put(bibMatchPointDetail.matchPointValue(), resultMatchPoint);
							
							// Add it to the result
							resultMatchPointCluster.getMatchPoints().add(resultMatchPoint);
						}

						// Add a bib to this match point result
						ResultMatchPointBib resultMatchPointBib = new ResultMatchPointBib();
						resultMatchPointBib.setId(bibMatchPointDetail.bibId());
						resultMatchPointBib.setTitle(bibMatchPointDetail.title());
						resultMatchPointBib.setHostName(bibMatchPointDetail.hostName());
						resultMatchPointBib.setProcessVersion(bibMatchPointDetail.processVersion());
						resultMatchPointBib.setNumberOfMatchPoints(bibMatchPointDetail.numberOfMatchPoints());

						// Add it to the match point result
						resultMatchPoint.getBibs().add(resultMatchPointBib);
					}

					// Finished processing the match points 
					return(resultMatchPointCluster);
				})
				.flatMapMany( a -> Flux.from(bibRepository.findDistinctIdentifiersFor(id, namesspacesUsedForClustering)) )
				.collectList()
				.map( identifiers -> {
					// Loop through each of the Unique identifiers
					for (BibRepository.Identifier identifier : identifiers) {
						// We need to calculate the uuid for this identifier
						// TODO: This has been copied as the various parts are private as that area was being worked on I did not change it
						// It needs to be changed so we do not use copied code
						String s = String.format("%s:%s:%s", MATCHPOINT_ID, identifier.namespace(), identifier.value());
						MatchPoint matchPoint = MatchPoint.buildFromString(s, identifier.namespace());

						// Now we have the match point value, look it up
						ResultMatchPoint resultMatchPoint = matchPointMap.get(matchPoint.getValue());
						if (resultMatchPoint != null) {
							resultMatchPoint.setNamespace(identifier.namespace());
							resultMatchPoint.setValue(identifier.value());
						}
					}

					// Finished processing the identifiers 
					return(resultMatchPointCluster);
				})
				.map( a-> {
					// Finally we sort the match points
					Collections.sort(resultMatchPointCluster.getMatchPoints(), (resultMatchPoint1, resultMatchPoint2) -> resultMatchPoint1.getMatchPointValue().toString().compareTo(resultMatchPoint2.getMatchPointValue().toString()));

					// Finished processing the identifiers 
					return(resultMatchPointCluster);
				})
		);
	}
}
