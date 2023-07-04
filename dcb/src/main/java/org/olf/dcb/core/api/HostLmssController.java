package org.olf.dcb.core.api;

import java.util.UUID;

import javax.validation.Valid;

import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.storage.HostLmsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Mono;

@Validated
@Secured({ "ADMIN" })
@Controller("/hostlmss")
@Tag(name = "HostLMSs")
public class HostLmssController {

	private static final Logger log = LoggerFactory.getLogger(HostLmssController.class);

	private HostLmsRepository hostLMSRepository;

	public HostLmssController(HostLmsRepository hostLMSRepository) {
		this.hostLMSRepository = hostLMSRepository;
	}

	@Operation(summary = "Browse HOST LMSs", description = "Paginate through the list of known host LMSs", parameters = {
			@Parameter(in = ParameterIn.QUERY, name = "number", description = "The page number", schema = @Schema(type = "integer", format = "int32"), example = "1"),
			@Parameter(in = ParameterIn.QUERY, name = "size", description = "The page size", schema = @Schema(type = "integer", format = "int32"), example = "100") })
	@Get("/{?pageable*}")
	public Mono<Page<DataHostLms>> list(@Parameter(hidden = true) @Valid Pageable pageable) {
		if (pageable == null) {
			pageable = Pageable.from(0, 100);
		}

		return Mono.from(hostLMSRepository.findAll(pageable));
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
}
