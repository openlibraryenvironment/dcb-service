package org.olf.reshare.dcb.request.resolution;

import java.util.UUID;

import org.olf.reshare.dcb.core.model.HostLms;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;

@Serdeable
@Data
public class Bib {
	private final UUID id;
	private final String bibRecordId;
	private final HostLms hostLms;
}
