package org.olf.reshare.dcb.item.availability;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;

@Data
@Serdeable
public class Status {
	private final String code;
	private final String displayText;
	@Nullable
	private final String dueDate;
}
