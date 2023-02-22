package org.olf.reshare.dcb.core.model;

import java.util.UUID;

import javax.validation.constraints.NotNull;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.persistence.Column;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

//@Serdeable
//@ExcludeFromGeneratedCoverageReport
//@MappedEntity
//public class AgencyDataImpl implements Agency {
//	@NotNull
//	@NonNull
//	@Id
//	@Column(columnDefinition = "UUID")
//	private UUID id;
//
//	@Nullable
//	@Column(columnDefinition = "TEXT")
//	private String name;
//
//	public AgencyDataImpl() {
//	}
//
//	public AgencyDataImpl(UUID id, String name) {
//		this.id = id;
//		this.name = name;
//	}
//
//	public UUID getId() {
//		return id;
//	}
//
//	public AgencyDataImpl setId(UUID id) {
//		this.id = id;
//		return this;
//	}
//
//	public String getName() {
//		return name;
//	}
//
//	public AgencyDataImpl setName(String name) {
//		this.name = name;
//		return this;
//	}
//
//}
