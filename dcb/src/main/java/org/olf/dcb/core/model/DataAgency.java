package org.olf.dcb.core.model;

import static io.micronaut.data.annotation.Relation.Kind.MANY_TO_ONE;

import java.time.Instant;
import java.util.UUID;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.DateUpdated;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import services.k_int.data.querying.DefaultQueryField;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

@Data
@Serdeable
@ExcludeFromGeneratedCoverageReport
@MappedEntity(value = "agency")
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Builder
@Accessors(chain = true)
@ToString(onlyExplicitlyIncluded = true)
public class DataAgency implements Agency {
	public static final String BASIC_BARCODE_AND_PIN = "BASIC/BARCODE+PIN";
	public static final String BASIC_BARCODE_AND_NAME = "BASIC/BARCODE+NAME";

	@ToString.Include
	@NonNull
	@Id
	@TypeDef(type = DataType.UUID)
	private UUID id;

	@Nullable
	@DateCreated
	private Instant dateCreated;

	@Nullable
	@DateUpdated
	private Instant dateUpdated;

	@ToString.Include
	@NonNull
	@Size(max = 32)
	@DefaultQueryField
	private String code;

	@ToString.Include
	@NonNull
	@Size(max = 200)
	private String name;

	@ToString.Include
	@NonNull
	@Relation(value = MANY_TO_ONE)
	private DataHostLms hostLms;

	@ToString.Include
	@Nullable
	@Size(max = 64)
	private String authProfile;

	@ToString.Include
	@Nullable
	@Size(max = 200)
	private String idpUrl;

	private Double longitude;

	private Double latitude;

	// Does this agency participate in interlending
	private Boolean isSupplyingAgency;
	private Boolean isBorrowingAgency;

	public static class DataAgencyBuilder {
		public DataAgencyBuilder() {
		}
		// Lombok will fill in the fields and methods
	}
}
