package io.micronaut.configuration.metrics.binder.web;

import java.util.Optional;

import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.inject.BeanDefinition;

/**
 * Micronaut condition allowing us to enable our metric extension based on the presence
 * of the system metrics. Needs to be in this package as the class we depend on is
 * package private. 
 */
public class SystemClientMetricsEnabled implements Condition {

	@Override
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public boolean matches(ConditionContext context) {
		Optional<BeanDefinition> def = context.findBeanDefinitions(ClientMetricsFilter.class).stream()
			.findFirst();
		
		if (def.isEmpty()) return false;
		
		return def.get().isEnabled(context.getBeanContext());
	}

}
