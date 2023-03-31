package org.olf.reshare.dcb.request.resolution;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;

import java.util.UUID;

@Serdeable
@Data
public class Bib {
	private final UUID id;
	private final String bibRecordId;
	private final String hostLmsCode;
}
