package org.olf.dcb.core.interaction.alma;

/**
 * The supplier's item barcode already belongs to an item in the borrowing library's Alma. The
 * barcode itself is left out of the message, which reaches logs and the request's audit trail.
 */
public class DuplicateItemBarcodeException extends RuntimeException {
	public DuplicateItemBarcodeException(String hostLmsCode, String existingBibId) {
		super("The supplier's item barcode is already used by an item in " + hostLmsCode
			+ (existingBibId != null ? " (bib " + existingBibId + ")" : "")
			+ ", and Alma allows a barcode on only one item, so the virtual item cannot be created."
			+ " The two libraries' barcode ranges overlap.");
	}
}
