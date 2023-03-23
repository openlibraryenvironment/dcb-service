package org.olf.reshare.dcb.core.api;

import static org.olf.reshare.dcb.core.Constants.UUIDs.NAMESPACE_DCB;
import javax.validation.Valid;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.olf.reshare.dcb.core.model.LocationSymbol;

import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Mono;
import services.k_int.utils.UUIDUtils;
import org.olf.reshare.dcb.storage.LocationSymbolRepository;


@Controller("/symbols")
@Tag(name = "Symbols")
@Secured(SecurityRule.IS_ANONYMOUS)
public class SymbolController {

        private LocationSymbolRepository locationSymbolRepository;

	public SymbolController(LocationSymbolRepository locationSymbolRepository) {
		this.locationSymbolRepository = locationSymbolRepository;
	}

        @Operation(
                summary = "Browse Symbols",
                description = "Paginate through the list of known symbols",
                parameters = {
                        @Parameter(in = ParameterIn.QUERY, name = "number", description = "The page number", schema = @Schema(type = "integer", format = "int32"), example = "1"),
                        @Parameter(in = ParameterIn.QUERY, name = "size", description = "The page size", schema = @Schema(type = "integer", format = "int32"), example = "100")}
        )
        @Get("/{?pageable*}")
        public Mono<Page<LocationSymbol>> list(@Parameter(hidden = true) @Valid Pageable pageable) {
                if (pageable == null) {
                        pageable = Pageable.from(0, 100);
                }

                return Mono.from(locationSymbolRepository.findAll(pageable));
        }

        @Get("/{id}")
        public Mono<LocationSymbol> show(UUID id) {
                return Mono.from(locationSymbolRepository.findById(id));
        }

}
