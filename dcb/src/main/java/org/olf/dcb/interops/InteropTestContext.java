package org.olf.dcb.interops;

import org.olf.dcb.core.interaction.HostLmsClient;

import java.util.UUID;
import java.util.List;
import java.util.Map;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
@AllArgsConstructor
@Serdeable
public class InteropTestContext {

	HostLmsClient hostLms;
 	Map<String,Object> values;
	boolean success;
}
