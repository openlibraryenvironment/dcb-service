package org.olf.dcb.ingest.job;

import java.util.UUID;

import org.olf.dcb.core.error.DcbException;
import org.olf.dcb.ingest.model.IngestRecord;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;


@Getter
@RequiredArgsConstructor
@EqualsAndHashCode
@ToString
@Builder
public class IngestOperation {
	
	@NonNull
	@NotNull
	private final UUID sourceId;
	
	@Nullable
	private final IngestRecord ingest;
	
	@Nullable
	private final DcbException exception;
	
}
