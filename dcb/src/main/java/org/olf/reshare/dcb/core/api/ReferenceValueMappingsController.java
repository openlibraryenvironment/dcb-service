package org.olf.reshare.dcb.core.api;
import javax.validation.Valid;

import org.olf.reshare.dcb.core.model.RefdataValue;
import org.olf.reshare.dcb.core.model.ReferenceValueMapping;
import org.olf.reshare.dcb.storage.RefdataValueRepository;
import org.olf.reshare.dcb.storage.ReferenceValueMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Validated
@Secured({"ADMIN"})
@Controller("/referenceValueMappings")
@Tag(name = "Reference Value mappings")
public class ReferenceValueMappingsController {

    private static final Logger log = LoggerFactory.getLogger(ReferenceValueMappingsController.class);

    private ReferenceValueMappingRepository referenceValueMappingRepository;

    public ReferenceValueMappingsController(ReferenceValueMappingRepository referenceValueMappingRepository) {
        this.referenceValueMappingRepository = referenceValueMappingRepository;
    }

    @Operation(
            summary = "Browse HOST LMSs",
            description = "Paginate through the list of known host LMSs",
            parameters = {
                    @Parameter(in = ParameterIn.QUERY, name = "number", description = "The page number", schema = @Schema(type = "integer", format = "int32"), example = "1"),
                    @Parameter(in = ParameterIn.QUERY, name = "size", description = "The page size", schema = @Schema(type = "integer", format = "int32"), example = "100")}
    )
    @Get("/{?pageable*}")
    public Mono<Page<ReferenceValueMapping>> list(@Parameter(hidden = true) @Valid Pageable pageable) {
        if (pageable == null) {
            pageable = Pageable.from(0, 100);
        }

        return Mono.from(referenceValueMappingRepository.findAll(pageable));
    }

    @Get("/{id}")
    public Mono<ReferenceValueMapping> show(UUID id) {
        return Mono.from(referenceValueMappingRepository.findById(id));
    }


}
