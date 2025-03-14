package org.olf.dcb.core.model;

import java.util.UUID;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.NonNull;


import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.security.annotation.UpdatedBy;
import jakarta.validation.constraints.Size;

import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.olf.dcb.core.audit.Auditable;

@Data
@Serdeable
@Builder
@ToString(onlyExplicitlyIncluded = true)
@MappedEntity
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor

public class Person implements Auditable {
	@ToString.Include
	@NonNull
	@Id
	@TypeDef( type = DataType.UUID)
	private UUID id;

	@NonNull
	@Size(max = 128)
	private String firstName;

	@NonNull
	@Size(max = 128)
	private String lastName;


	@NonNull
	@Relation(value = Relation.Kind.MANY_TO_ONE)
	private Role role;

	@NonNull
	@Size(max = 255)
	private String email;

	private Boolean isPrimaryContact;

	@Nullable
	@UpdatedBy
	private String lastEditedBy;

	@Nullable
	private String reason;

	@Nullable
	private String changeCategory;

	@Nullable
	private String changeReferenceUrl;
}
