package org.olf.reshare.dcb.bib.gokb;

import java.util.List;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record GokbTipp (
	String tippTitleName,
	String titleType,
	List<GokbIdentifier> identifiers
	) {}
