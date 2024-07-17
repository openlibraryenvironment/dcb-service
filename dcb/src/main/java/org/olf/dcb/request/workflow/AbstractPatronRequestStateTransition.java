package org.olf.dcb.request.workflow;

import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.List;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;

import lombok.Getter;

@Getter
abstract class AbstractPatronRequestStateTransition {
	// Name retained from original field to preserve public interface
	private final List<PatronRequest.Status> possibleSourceStatus;

	AbstractPatronRequestStateTransition(List<PatronRequest.Status> applicableStatuses) {
		possibleSourceStatus = applicableStatuses;
	}

	private boolean notInApplicableRequestStatus(RequestWorkflowContext context) {
		final var requestStatus = getValueOrNull(context,
			RequestWorkflowContext::getPatronRequest, PatronRequest::getStatus);

		return notInApplicableRequestStatus(requestStatus);
	}

	private boolean notInApplicableRequestStatus(PatronRequest.Status requestStatus) {
		if (requestStatus == null) {
			return true;
		}

		return !possibleSourceStatus.contains(requestStatus);
	}

	public boolean isApplicableFor(RequestWorkflowContext context) {
		if (notInApplicableRequestStatus(context)) {
			return false;
		}

		return checkApplicability(context);
	}

	protected abstract boolean checkApplicability(RequestWorkflowContext context);
}
