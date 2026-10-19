package org.olf.dcb.request.notes;

import java.util.Collection;
import java.util.List;

/**
 * Who is writing or reading notes and tags, from verified claims. Consortium staff speak for the
 * consortium whatever agencies they also hold.
 */
public record RequestNoteCaller(boolean consortium, Collection<String> agencyCodes) {
	public RequestNoteCaller {
		agencyCodes = agencyCodes == null ? List.of() : List.copyOf(agencyCodes);
	}
}
