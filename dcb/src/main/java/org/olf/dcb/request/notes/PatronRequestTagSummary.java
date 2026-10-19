package org.olf.dcb.request.notes;

import java.util.List;

/** One tag on a request, with every library that applied it and whether the consortium did. */
public record PatronRequestTagSummary(String code, String label, List<String> agencyCodes, boolean consortium) {
}
