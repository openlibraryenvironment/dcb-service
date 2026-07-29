package org.olf.dcb.request.lifecycle.ncip;

public record NcipBibliographicDescription(
	String title,
	String author,
	String bibliographicRecordIdentifier,
	String bibliographicRecordAgencyId,
	String itemIdentifierValue,
	String edition) {

	public boolean hasContent() {
		return hasText(title)
			|| hasText(author)
			|| hasText(bibliographicRecordIdentifier)
			|| hasText(itemIdentifierValue)
			|| hasText(edition);
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
