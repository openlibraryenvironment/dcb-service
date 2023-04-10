package org.olf.reshare.dcb.request.resolution;

import java.util.List;
import java.util.UUID;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.AllArgsConstructor;
import io.micronaut.core.annotation.Creator;

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
