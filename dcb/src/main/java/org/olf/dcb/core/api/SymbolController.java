package org.olf.dcb.core.api;

import java.util.UUID;

import org.olf.dcb.core.api.serde.LocationSymbolDTO;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.model.LocationSymbol;
import org.olf.dcb.security.RoleNames;
import org.olf.dcb.storage.LocationRepository;
import org.olf.dcb.storage.LocationSymbolRepository;

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
import jakarta.validation.Valid;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;

@Controller("/symbols")
@Validated
@Secured(RoleNames.ADMINISTRATOR)
@Tag(name = "Symbols")
public class SymbolController {

	private LocationRepository locationRepository;
	private LocationSymbolRepository locationSymbolRepository;

	public SymbolController(LocationSymbolRepository locationSymbolRepository, LocationRepository locationRepository) {
		this.locationSymbolRepository = locationSymbolRepository;
		this.locationRepository = locationRepository;
	}

	@Operation(summary = "Browse Symbols", description = "Paginate through the list of known symbols", parameters = {
			@Parameter(in = ParameterIn.QUERY, name = "number", description = "The page number", schema = @Schema(type = "integer", format = "int32"), example = "1"),
			@Parameter(in = ParameterIn.QUERY, name = "size", description = "The page size", schema = @Schema(type = "integer", format = "int32"), example = "100") })
	@Get("/{?pageable*}")
	public Mono<Page<LocationSymbol>> list(@Parameter(hidden = true) @Valid Pageable pageable) {
		if (pageable == null) {
			pageable = Pageable.from(0, 100);
		}

		return Mono.from(locationSymbolRepository.queryAll(pageable));
	}

	@Get("/{id}")
	public Mono<LocationSymbol> show(UUID id) {
		return Mono.from(locationSymbolRepository.findById(id));
	}

	@Post("/")
	public Mono<LocationSymbol> postLocationSymbol(@Body LocationSymbolDTO symbol) {

		return Mono.from(locationRepository.findById(symbol.locationId()))
			.zipWith( Mono.just(LocationSymbol.builder().id(symbol.id()).authority(symbol.authority()).code(symbol.code())) )
			.map( TupleUtils.function( (loc, builder) -> builder.location(loc).build() ))
			.flatMap( ls -> Mono.from(locationSymbolRepository.existsById(ls.getId()))
					.flatMap(
							exists -> Mono.fromDirect(exists ? locationSymbolRepository.update(ls) : locationSymbolRepository.save(ls))));
	}

}
