package org.olf.dcb.core.api;
import java.util.UUID;

import jakarta.validation.Valid;

import org.olf.dcb.storage.NumericRangeMappingRepository;
import org.olf.dcb.core.model.NumericRangeMapping;
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
@Secured({"ADMIN"})
@Controller("/numericRangeMappingController")
@Tag(name = "Numeric Range Mapping")
public class NumericRangeMappingController {

    private static final Logger log = LoggerFactory.getLogger(NumericRangeMappingController.class);

    private NumericRangeMappingRepository numericRangeMappingRepository;

    public NumericRangeMappingController(NumericRangeMappingRepository numericRangeMappingRepository) {
        this.numericRangeMappingRepository = numericRangeMappingRepository;
    }

    @Operation(
            summary = "Browse Numeric Range Mappings",
            description = "Paginate through the list of known host LMSs",
            parameters = {
                    @Parameter(in = ParameterIn.QUERY, name = "number", description = "The page number", schema = @Schema(type = "integer", format = "int32"), example = "1"),
                    @Parameter(in = ParameterIn.QUERY, name = "size", description = "The page size", schema = @Schema(type = "integer", format = "int32"), example = "100")}
    )
    @Get("/{?pageable*}")
    public Mono<Page<NumericRangeMapping>> list(@Parameter(hidden = true) @Valid Pageable pageable) {
        if (pageable == null) {
            pageable = Pageable.from(0, 100);
        }

        return Mono.from(numericRangeMappingRepository.queryAll(pageable));
    }

    @Get("/{id}")
    public Mono<NumericRangeMapping> show(UUID id) {
        return Mono.from(numericRangeMappingRepository.findById(id));
    }

    @Post("/")
    public Mono<NumericRangeMapping> postHostLMS(@Body NumericRangeMapping nrm) {
        return Mono.from(numericRangeMappingRepository.existsById(nrm.getId()))
                        .flatMap(exists -> Mono.fromDirect(exists ? numericRangeMappingRepository.update(nrm) : numericRangeMappingRepository.save(nrm)));
    }

}
