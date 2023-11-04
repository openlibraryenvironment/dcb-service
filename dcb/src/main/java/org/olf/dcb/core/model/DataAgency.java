package org.olf.dcb.core.model;

import java.util.UUID;

import jakarta.validation.constraints.Size;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.DateUpdated;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.core.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import services.k_int.data.querying.DefaultQueryField;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;
import lombok.ToString;
import java.time.Instant;

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

	@NonNull
	@Relation(value = Relation.Kind.MANY_TO_ONE)
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

	public static class DataAgencyBuilder {
		public DataAgencyBuilder() {
		}
		// Lombok will fill in the fields and methods
	}

}
