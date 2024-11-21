package org.olf.dcb.core.model;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.olf.dcb.request.fulfilment.SupplierRequestStatusCode;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

import java.util.UUID;

/**
 * Important reminder: This class needs to be representative of many different target systems.
 * Strings are chosen in many places to give us some flexibility to adapt to different targets - when adding properties here
 * remember to bear in mind that the datatype of the system you have in mind might not map to other systems.
 */
@EqualsAndHashCode(callSuper = false)
@Serdeable
@ExcludeFromGeneratedCoverageReport
@SuperBuilder(toBuilder = true)
@MappedEntity(value = "supplier_request")
@RequiredArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Accessors(chain = true)
@ToString(onlyExplicitlyIncluded = true)
@Setter
@Getter
public class SupplierRequest extends BaseSupplierRequest<SupplierRequest> {
	@Id
	@TypeDef(type = DataType.UUID)
	private UUID id;

	@Nullable
	@Builder.Default
	private Boolean isActive = true;

	public SupplierRequest placed(
		String localId, String localStatus,
		String rawLocalStatus, String localItemId,
		String localItemBarcode) {
		super.setLocalId(localId);
		super.setLocalStatus(localStatus);
		super.setRawLocalStatus(rawLocalStatus);
		if (localItemId != null)
			super.setLocalItemId(localItemId);
		if (localItemBarcode != null)
			super.setLocalItemBarcode(localItemBarcode);
		super.setStatusCode(SupplierRequestStatusCode.PLACED);
		return this;
	}
}
