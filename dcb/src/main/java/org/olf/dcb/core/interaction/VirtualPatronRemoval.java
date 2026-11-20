package org.olf.dcb.core.interaction;

/**
 * How a library system can remove a virtual patron DCB created in it, when staff ask.
 * DCB never does either on its own.
 */
public enum VirtualPatronRemoval {
	/** The record is deleted in the library's system, and DCB forgets it. */
	DELETE,
	/** The record stays and is expired; DCB renews it if the borrower requests from that library again. */
	EXPIRE
}
