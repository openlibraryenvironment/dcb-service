package org.olf.dcb.core.api;

import java.util.UUID;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.storage.HostLmsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import reactor.core.publisher.Mono;

@Controller("/hostlmss")
@Validated
@Secured(SecurityRule.IS_ANONYMOUS)
@Tag(name = "Host LMS Api")
public class HostLmssController {

	private static final Logger log = LoggerFactory.getLogger(HostLmssController.class);

	private final HostLmsRepository hostLMSRepository;
	private final HostLmsService lmsService;

	public HostLmssController(HostLmsRepository hostLMSRepository, HostLmsService lmsService) {
		this.hostLMSRepository = hostLMSRepository;
		this.lmsService = lmsService;
	}

	@Operation(summary = "Browse HOST LMSs", description = "Paginate through the list of known host LMSs", parameters = {
			@Parameter(in = ParameterIn.QUERY, name = "number", description = "The page number", schema = @Schema(type = "integer", format = "int32"), example = "1"),
			@Parameter(in = ParameterIn.QUERY, name = "size", description = "The page size", schema = @Schema(type = "integer", format = "int32"), example = "100") })
	@Get("/{?pageable*}")
	public Mono<Page<DataHostLms>> list(@Parameter(hidden = true) @Valid Pageable pageable) {
		if (pageable == null) {
			pageable = Pageable.from(0, 100);
		}

		return Mono.from(hostLMSRepository.queryAll(pageable));
	}

	@Get("/{id}")
	public Mono<DataHostLms> show(UUID id) {
		return Mono.from(hostLMSRepository.findById(id));
	}

	@Post("/")
	public Mono<DataHostLms> postHostLMS(@Body DataHostLms hostLMS) {
		return Mono.from(hostLMSRepository.existsById(hostLMS.getId()))
				.flatMap(exists -> Mono.fromDirect(exists ? hostLMSRepository.update(hostLMS) : hostLMSRepository.save(hostLMS)));
	}
	
	private Mono<MutableHttpResponse<Object>> startBackgroundDataRemoval( final @NonNull HostLms hostLms ) {
		lmsService.deleteHostLmsData( hostLms )
			.subscribe(
				lms -> log.info("Successfully removed data for hostLms [{}]", lms),
				e -> {
					log.error("Error when removing data for hostLms [{}]", hostLms, e);
				});
	
		return Mono.just(HttpResponse.accepted());
	}
	
	@Delete("/{id}")
	@ExecuteOn(TaskExecutors.BLOCKING)
	public Mono<MutableHttpResponse<Object>> deleteHostLMS( @NonNull final UUID id ) {
		return Mono.from(hostLMSRepository.findById(id))
			.doOnSuccess( lms -> {
				
				if (lms != null) {
					log.info("Removing data for hostLms [{}]", lms);
				} else {
					log.warn("Request to remove host lms by none-existant ID [{}]", id);
				}
				
			})
			.flatMap( this::startBackgroundDataRemoval )
			.defaultIfEmpty(HttpResponse.notFound());
	}
}
