package org.olf.reshare.dcb.request.resolution;

import io.micronaut.core.annotation.Creator;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Builder
@Serdeable
@Data
public class ClusteredBib {
	private UUID id;
	private String title;
	private List<Bib> bibs;
}
