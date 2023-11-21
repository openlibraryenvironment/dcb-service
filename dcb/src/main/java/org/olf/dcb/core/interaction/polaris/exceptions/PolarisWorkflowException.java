package org.olf.dcb.core.interaction.polaris.exceptions;

public class PolarisWorkflowException extends RuntimeException {
	public PolarisWorkflowException(String workflowtype) {
		super("Unexpected Polaris workflow response for: " + workflowtype);
	}
}
