package org.olf.dcb.core.api;

import static org.olf.dcb.security.RoleNames.ADMINISTRATOR;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.olf.dcb.core.api.serde.ClusterRecordDTO;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.clustering.ClusterRecord;
import org.olf.dcb.core.svc.RecordClusteringService;
import org.olf.dcb.storage.BibRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpResponse;
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
	
	public ClusterRecordController(
			RecordClusteringService recordClusteringService,
			BibRepository bibRepository,
			ObjectMapper objectMapper) {
		this.recordClusteringService = recordClusteringService;
		this.bibRepository = bibRepository;
		this.objectMapper = objectMapper;
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
	@Get("/{id}/reprocess")
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
}
