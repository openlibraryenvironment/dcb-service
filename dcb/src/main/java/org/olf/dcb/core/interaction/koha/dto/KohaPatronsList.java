package org.olf.dcb.core.interaction.koha.dto;

import io.micronaut.serde.annotation.Serdeable;
import java.util.ArrayList;

/**
 * Represents a root-level JSON array of Koha patrons.
 * This class exists to provide a concrete type for Micronaut's JSON deserializer,
 * bypassing Java's generic type erasure issues for List<KohaPatron>.
 */
@Serdeable
public class KohaPatronsList extends ArrayList<KohaPatron> {

	// No additional fields or methods are needed.
	// Jackson will automatically deserialize the JSON array into this list.

}
