package org.olf.dcb.core.api.serde;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Serdeable
public class ImportCommand {
	String profile;
	String url;
}