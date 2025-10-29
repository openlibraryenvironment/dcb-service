package org.olf.dcb.core.api;

import static org.olf.dcb.security.RoleNames.ADMINISTRATOR;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;
import org.olf.dcb.core.api.serde.ClusterRecordDTO;
import org.olf.dcb.core.clustering.RecordClusteringService;
import org.olf.dcb.core.clustering.model.MatchPoint;
import org.olf.dcb.core.model.BibIdentifier;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.clustering.model.ClusterRecord;
import org.olf.dcb.core.svc.BibRecordService;
import org.olf.dcb.storage.BibRepository;
import org.olf.dcb.storage.MatchPointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
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


@Controller("/clusters")
@Validated
@Secured(SecurityRule.IS_ANONYMOUS)
@Tag(name = "Cluster Records (Read only)")
@Transactional(readOnly = true)
public class ClusterRecordController {

	private static final Logger log = LoggerFactory.getLogger(ClusterRecordController.class);

	private final RecordClusteringService recordClusteringService;
	private final BibRepository bibRepository;
	private final ObjectMapper objectMapper;
	private final MatchPointRepository matchPointRepository;
	private final BibRecordService bibRecordService;
	
	public ClusterRecordController(
			RecordClusteringService recordClusteringService,
			BibRepository bibRepository,
			ObjectMapper objectMapper,
			MatchPointRepository matchPointRepository,
			BibRecordService bibRecordService) {
		this.recordClusteringService = recordClusteringService;
		this.bibRepository = bibRepository;
		this.objectMapper = objectMapper;
		this.matchPointRepository = matchPointRepository;
		this.bibRecordService = bibRecordService;

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

	@Operation(summary = "Get the match point details for a cluster")
	@Get("/{id}/matchPointDetails")
	public Mono<ClusterMatchPointDetailsDto> matchPointDetails(UUID id) {
		// Takes cluster UUID, returns the details of all match points.

		// The main reactive stream starts here - finding all member bib records for the given cluster ID.
		return Flux.from(bibRepository.findMemberBibsForCluster(id))

			// Use flatmap to perform async operation on each MemberBib that comes through the stream
			// This adds more details
			// `flatMap` subscribes to the inner Mono/Flux and flattens each
			// result back into the main stream.
			// We could probably have done this with nested Monos also
			.flatMap(memberBib -> {

				// We need lists of match points and identifiers for each member
				// So we create two separate Monos that will fetch this data.
				// First up are the match points. Grab a flux of them and use collectList
				Mono<List<MatchPoint>> matchPointsMono = Flux.from(
						matchPointRepository.findAllByBibId(memberBib.bibid())
					)
					.collectList();

				// To get identifiers, we will need full BibRecord object
				Mono<BibRecord> bibRecordMono = Mono.from(bibRepository.getById(memberBib.bibid()));
				// Then we can do a reactive chain off the bib record mono
				// And use 'flatMapMany' because `findAllIdentifiersForBib` returns a Flux,
				// And we want to flatten the items and put them in a list with collectList
				Mono<List<BibIdentifier>> identifiersMono = bibRecordMono
					.flatMapMany(bibRecordService::findAllIdentifiersForBib)
					.collectList();


				// We now have two Monos (matchPointsMono, identifiersMono) that can be executed concurrently.
				// Mono.zip is used here because it will take both Monos and wait for them both to complete
				// On completion these will be turned into a Tuple2
				return Mono.zip(matchPointsMono, identifiersMono)
					// Once the zip completes map performs synchronous transformation.
					// We take the results from the tuple and data we already have from memberBib to build BibDetailsDto.
					.map(tuple -> BibDetailsDto.builder()
						.bibId(memberBib.bibid())
						.title(memberBib.title())
						.hostLmsName(memberBib.sourcesystem())
						// We are lucky here - the source system on the member bib appears to be the Host LMS code
						// On the standard bib record this would have been the Host LMS UUID - we'd have needed another fetch
						.matchPoints(tuple.getT1()) // Result from matchPointsMono
						.identifiers(tuple.getT2()) // Result from identifiersMono
						.build()
					);
			})

			// At this point, the  stream is a Flux<BibDetailsDto> (one for
			// each bib in the cluster).
			// collectList waits for the Flux to complete and then emits a single Mono<List<BibDetailsDto>>.
			.collectList()

			// To finish, map transforms our bib details list onto the ClusterMatchPointDetailsDto we return
			// Which also includes the original cluster ID.
			.map(bibDetailsList -> ClusterMatchPointDetailsDto.builder()
				.clusterId(id)
				.bibs(bibDetailsList)
				.build()
			);
	}


	@Data
	@Builder
	@Serdeable
	public static class ClusterMatchPointDetailsDto {
		private UUID clusterId;
		private List<BibDetailsDto> bibs;
	}

	// This holds the details for a single bib record in the cluster
	@Data
	@Builder
	@Serdeable
	static class BibDetailsDto {
		private UUID bibId;
		private String title;
		private String hostLmsName;
		private List<BibIdentifier> identifiers;
		private List<MatchPoint> matchPoints;
	}
}

