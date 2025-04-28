package org.olf.dcb.core.model;

import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.UUID;

import io.micronaut.core.annotation.Creator;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Transient;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

@Setter
@Getter
@EqualsAndHashCode(callSuper = false)
@Serdeable
@ExcludeFromGeneratedCoverageReport
@Data
@MappedEntity("inactive_supplier_request")
@RequiredArgsConstructor(onConstructor_ = @Creator())
@SuperBuilder(toBuilder = true)
@Accessors(chain = true)
@ToString(onlyExplicitlyIncluded = true)
public class InactiveSupplierRequest extends BaseSupplierRequest<InactiveSupplierRequest> {
	@Id
	@TypeDef(type = DataType.UUID)
	private UUID id;

	@Transient
	public UUID getResolvedAgencyId() {
		return getValueOrNull(this, InactiveSupplierRequest::getResolvedAgency, DataAgency::getId);
	}
}
