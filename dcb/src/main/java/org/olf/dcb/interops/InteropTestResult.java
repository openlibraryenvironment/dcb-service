package org.olf.dcb.interops;

import java.util.UUID;
import java.util.List;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
@AllArgsConstructor
@Serdeable
public class InteropTestResult {

	String stage;
	String step;
	String result;
	List<String> notes;

}
