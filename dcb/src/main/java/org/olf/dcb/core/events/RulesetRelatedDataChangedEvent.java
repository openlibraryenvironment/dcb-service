package org.olf.dcb.core.events;

import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.rules.ObjectRuleset;

import io.micronaut.context.event.ApplicationEvent;

public class RulesetRelatedDataChangedEvent extends ApplicationEvent {

	private static final long serialVersionUID = 1L;

	public RulesetRelatedDataChangedEvent(ObjectRuleset source) {
		super(source);
	}
	
	public RulesetRelatedDataChangedEvent(DataHostLms source) {
		super(source);
	}
}
