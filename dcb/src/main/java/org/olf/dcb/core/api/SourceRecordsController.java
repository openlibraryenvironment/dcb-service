package org.olf.dcb.core.api;

import static org.olf.dcb.security.RoleNames.ADMINISTRATOR;

import java.time.Instant;

import org.olf.dcb.dataimport.job.model.SourceRecord;
import org.olf.dcb.storage.SourceRecordRepository;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import reactor.core.publisher.Mono;

@Validated
@Controller("/sourceRecords")
@Secured(ADMINISTRATOR)
@Tag(name = "Source Records Api")
public class SourceRecordsController {

	private static final int DEFAULT_PAGE_SIZE = 100;

	private final SourceRecordRepository sourceRecordRepository;

	public SourceRecordsController(SourceRecordRepository sourceRecordRepository) {
		this.sourceRecordRepository = sourceRecordRepository;
	}

	@Operation(
		summary = "Browse Source Records",
		description = "Paginate through source records filtered by last modification time.",
		parameters = {
			@Parameter(in = ParameterIn.QUERY, name = "since", description = "Return records modified after this timestamp", schema = @Schema(type = "string", format = "date-time"), example = "1970-01-01T00:00:00Z"),
			@Parameter(in = ParameterIn.QUERY, name = "number", description = "The page number", schema = @Schema(type = "integer", format = "int32"), example = "0"),
			@Parameter(in = ParameterIn.QUERY, name = "size", description = "The page size", schema = @Schema(type = "integer", format = "int32"), example = "100")
		}
	)
	@Get("/{?since,pageable*}")
	public Mono<Page<SourceRecord>> list(
		@Nullable Instant since,
		@Parameter(hidden = true) @Valid Pageable pageable
	) {
		final Instant effectiveSince = since == null ? Instant.EPOCH : since;
		final Pageable sortedPageable = withLastModifiedSort(pageable);

		return Mono.from(sourceRecordRepository.findAllByDateUpdatedAfter(effectiveSince, sortedPageable));
	}

	private Pageable withLastModifiedSort(@Nullable Pageable pageable) {
		final Pageable base = pageable == null ? Pageable.from(0, DEFAULT_PAGE_SIZE) : pageable;

		return base.order(Sort.Order.desc("lastModified"));
	}
}
