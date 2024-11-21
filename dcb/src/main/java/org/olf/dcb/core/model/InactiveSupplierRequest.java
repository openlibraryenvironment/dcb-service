package org.olf.dcb.core.model;

import io.micronaut.core.annotation.Creator;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

import java.util.UUID;

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
}
