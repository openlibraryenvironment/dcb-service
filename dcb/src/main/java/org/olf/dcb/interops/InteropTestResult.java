package org.olf.dcb.interops;

import java.util.Map;
import java.util.UUID;
import java.util.List;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

@Builder
@Data
@AllArgsConstructor
@Serdeable
public class InteropTestResult {

	String stage;
	String step;
	String result;

	@Singular("note")
	List<String> notes;

	// if we want to include the API raw response
	@Nullable Map<String, Object> response;
}
