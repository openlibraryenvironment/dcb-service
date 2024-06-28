package org.olf.dcb.request.workflow;

import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.List;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;

import lombok.Getter;

@Getter
class AbstractPatronRequestStateTransition {
	// Name retained from original field to preserve public interface
	private final List<PatronRequest.Status> possibleSourceStatus;

	AbstractPatronRequestStateTransition(List<PatronRequest.Status> applicableStatuses) {
		possibleSourceStatus = applicableStatuses;
	}

	protected boolean notInApplicableRequestStatus(RequestWorkflowContext context) {
		final var requestStatus = getValueOrNull(context,
			RequestWorkflowContext::getPatronRequest, PatronRequest::getStatus);

		return notInApplicableRequestStatus(requestStatus);
	}

	protected boolean notInApplicableRequestStatus(PatronRequest.Status requestStatus) {
		if (requestStatus == null) {
			return true;
		}

		return !possibleSourceStatus.contains(requestStatus);
	}
}
