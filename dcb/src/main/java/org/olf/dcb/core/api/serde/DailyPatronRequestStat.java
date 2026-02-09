package org.olf.dcb.core.api.serde;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Introspected
@Serdeable
public class DailyPatronRequestStat {
	private LocalDate dateCreated;
	private Long count;
	private String patronHostlmsCode;
	private String pickupLocationCode;
	private String statusCode;
	private UUID bibClusterId;
}
