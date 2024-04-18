package org.olf.dcb.core.model;

import java.util.*;

import io.micronaut.core.annotation.*;

import io.micronaut.data.annotation.*;
import io.micronaut.data.annotation.sql.*;
import jakarta.validation.constraints.Size;

import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@Serdeable
@Builder
@ToString(onlyExplicitlyIncluded = true)
@MappedEntity
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor

public class Person {
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
	@Size(max = 128)
	private String role;

	@NonNull
	@Size(max = 255)
	private String email;

	private Boolean isPrimaryContact;
}
