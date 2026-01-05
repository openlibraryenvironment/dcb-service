package org.olf.dcb.test;

import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import org.olf.dcb.request.workflow.PatronRequestStateTransition;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
public class TransitionFixture {
	@Inject
	RequestWorkflowContextHelper requestWorkflowContextHelper;

	public void attemptIfApplicable(
		PatronRequestStateTransition transition, PatronRequest patronRequest) {

		singleValueFrom(requestWorkflowContextHelper.fromPatronRequest(patronRequest)
			.flatMap(ctx -> {
				if (!transition.isApplicableFor(ctx)) {
					return Mono.error(
						new RuntimeException("Transition is not applicable for request"));
				}

				return transition.attempt(ctx);
			}));
	}

	public Boolean isApplicable(PatronRequestStateTransition transition,
		PatronRequest patronRequest) {

		return singleValueFrom(requestWorkflowContextHelper.fromPatronRequest(patronRequest)
			.map(transition::isApplicableFor));
	}
}
