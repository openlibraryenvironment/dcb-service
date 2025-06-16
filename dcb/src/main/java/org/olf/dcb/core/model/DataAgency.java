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
import io.micronaut.security.annotation.UpdatedBy;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.Accessors;
import org.olf.dcb.core.audit.*;
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
@ToString
public class DataAgency implements Agency, Auditable {
	public static final String BASIC_BARCODE_AND_PIN = "BASIC/BARCODE+PIN";
	public static final String BASIC_BARCODE_AND_NAME = "BASIC/BARCODE+NAME";

	@NonNull
	@Id
	@TypeDef(type = DataType.UUID)
	private UUID id;

	@Nullable
	@DateCreated
	@ToString.Exclude
	private Instant dateCreated;

	@Nullable
	@DateUpdated
	@ToString.Exclude
	private Instant dateUpdated;

	@NonNull
	@Size(max = 32)
	@DefaultQueryField
	private String code;

	@NonNull
	@Size(max = 200)
	private String name;

	@NonNull
	@Relation(value = MANY_TO_ONE)
	private DataHostLms hostLms;

	@Nullable
	@Size(max = 64)
	private String authProfile;

	@Nullable
	@Size(max = 200)
	private String idpUrl;

	@ToString.Exclude
	private Double longitude;

	@ToString.Exclude
	private Double latitude;

	@Nullable
	// Does this agency participate in interlending
	private Boolean isSupplyingAgency;

	@Nullable
	private Boolean isBorrowingAgency;

	public static class DataAgencyBuilder {
		public DataAgencyBuilder() {
		}
		// Lombok will fill in the fields and methods
	}
	
	@Nullable
	@UpdatedBy
	private String lastEditedBy;
	
	@Nullable
	private String reason;

	@Nullable
	private String changeCategory;

	@Nullable
	private String changeReferenceUrl;

	@Nullable
	private Integer maxConsortialLoans;
}
