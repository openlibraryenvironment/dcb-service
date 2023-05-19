package org.olf.reshare.dcb.core.model.clustering;

import java.util.UUID;

import org.olf.reshare.dcb.core.Constants;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;
import services.k_int.utils.UUIDUtils;

@Builder(toBuilder = true)
@Data
@RequiredArgsConstructor
@AllArgsConstructor(onConstructor_ = @Creator())
@Serdeable
@MappedEntity
@ExcludeFromGeneratedCoverageReport
public class MatchPoint {
	private static final String PREFIX_VALUE = "MatchPoint";
	
	public static MatchPoint buildFromString ( String input ) {
		
		final String valueStr = PREFIX_VALUE + ":" + input;
		
		final UUID value = UUIDUtils.nameUUIDFromNamespaceAndString(Constants.UUIDs.NAMESPACE_DCB, valueStr);
		
		return builder()
			.value(value)
			.build();
	}
	
	@Id
	@AutoPopulated
	@TypeDef(type = DataType.UUID)
	private UUID id;

	@NonNull
	@TypeDef(type = DataType.UUID)
	private UUID value;
	
	@NonNull
	@TypeDef(type = DataType.UUID)
	private UUID bibId;
	
}
