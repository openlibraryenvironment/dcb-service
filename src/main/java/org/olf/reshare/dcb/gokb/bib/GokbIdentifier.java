package org.olf.reshare.dcb.gokb.bib;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record GokbIdentifier(
		String namespace,
		String value,
		String namespaceName) {
}
