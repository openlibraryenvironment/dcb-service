package org.olf.dcb.core.model;

import java.util.UUID;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.NonNull;

import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Id;
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

/** <p> A class representing a functional setting of an entity - for example whether 'pickup anywhere' functionality is enabled.
 * </p><br>
 * <p> This class contains information such as a functional setting's name, its enabled status and an optional description.
 * Valid names are stored in the FunctionalSettingType enum.
 * </p><br>
 * <p>A consortium will have a one-to-many relationship with its functional settings.
 * This could also be replicated for other entities such as libraries.
 * </p><br>
 * The UpdateFunctionalSetting data fetcher provides the functionality for a functional setting to be toggled via DCB Admin.
 * */

@Data
@Serdeable
@Builder
@ToString(onlyExplicitlyIncluded = true)
@MappedEntity
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
public class FunctionalSetting implements Auditable {

	@ToString.Include
	@NonNull
	@Id
	@TypeDef(type = DataType.UUID)
	private UUID id;

	@NonNull
	@TypeDef(type = DataType.STRING)
	private FunctionalSettingType name;

	@NonNull
	private Boolean enabled;

	@Nullable
	@Size(max = 200)
	private String description;

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
