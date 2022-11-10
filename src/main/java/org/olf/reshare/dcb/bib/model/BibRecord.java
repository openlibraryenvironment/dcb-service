package org.olf.reshare.dcb.bib.model;

import java.util.UUID;

import javax.validation.constraints.NotNull;

import io.micronaut.context.annotation.Type;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import jakarta.persistence.Column;

@MappedEntity
public class BibRecord {

	@NotNull
	@NonNull
	@Id
  @AutoPopulated
  private String id;
	
	@NotNull
	@NonNull
	@Column(columnDefinition = "TEXT")
	private String title;

	public String getId() {
		return id;
	}

	public BibRecord setId(String id) {
		this.id = id;
		return this;
	}

	public String getTitle() {
		return title;
	}

	public BibRecord setTitle(String title) {
		this.title = title;
		return this;
	}
}