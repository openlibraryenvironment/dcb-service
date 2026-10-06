package org.olf.dcb.core.interaction;

import io.micronaut.core.annotation.Nullable;

/**
 * The patron and copy a Host LMS is asked about. Every id is the Host LMS's own.
 * <p>
 * The holding id is optional because DCB does not record one for a supplier item; an adapter
 * that needs it looks it up.
 */
public record RequestOptionsQuery(
	String localPatronId,
	@Nullable String localBibId,
	@Nullable String localHoldingId,
	String localItemId,
	@Nullable String localItemBarcode) {}
