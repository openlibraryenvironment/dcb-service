package org.olf.reshare.dcb.api;

import javax.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;

import org.olf.reshare.dcb.model.BibRecord;
import org.olf.reshare.dcb.storage.BibRepository;
import org.reactivestreams.Publisher;

import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Schema;

@Controller("/bibs")
@Tag(name = "Bib API")
public class BibController {

  private final BibRepository _bibRepository;

  public BibController ( BibRepository bibRepository  ) {
  	_bibRepository = bibRepository;
  }
	
	@SingleResult
  @Secured(SecurityRule.IS_ANONYMOUS)
	@Operation(
		summary = "Browse Bibs",
		description = "Paginate through the list of known Bibilographic entries",
		parameters = {
				@Parameter(in = ParameterIn.QUERY, name = "number", description="The page number", schema = @Schema(type = "integer", format = "int32"), example = "1"),
				@Parameter(in = ParameterIn.QUERY, name = "size", description="The page size", schema = @Schema(type = "integer", format = "int32"), example = "100")}
	)
  @Get("/{?pageable*}")
  public Publisher<Page<BibRecord>> list(@Parameter(hidden = true) @Valid Pageable pageable ) {
		if (pageable == null) {
			pageable = Pageable.from(0, 100);
		}
		
    Publisher<Page<BibRecord>> results = _bibRepository.findAll( pageable );
    return results;
  }
}