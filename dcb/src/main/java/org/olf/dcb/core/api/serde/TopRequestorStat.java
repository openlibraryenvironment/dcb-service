package org.olf.dcb.core.api.serde;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Introspected
@Serdeable
public class TopRequestorStat {
	private String libraryCode;
	private Integer activeRequestCount;
	private String patronBarcode;
}
