package org.olf.dcb.request.resolution;

import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.model.BibRecord;

import io.micronaut.core.annotation.Creator;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Builder
@Serdeable
@Data
public class ClusteredBib {
	private UUID id;
	private String title;
	private List<BibRecord> bibs;
}
