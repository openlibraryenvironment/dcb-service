package org.olf.dcb.core.audit;

import java.util.stream.Stream;
import jakarta.inject.Singleton;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.security.utils.SecurityService;
import lombok.RequiredArgsConstructor;


import org.slf4j.*;

@Singleton
@RequiredArgsConstructor
public class AuditInterceptor implements MethodInterceptor<Object, Object> {

	private static Logger log = LoggerFactory.getLogger(AuditInterceptor.class);
	private final SecurityService security;

	@Override
	public Object intercept(MethodInvocationContext<Object, Object> context) {
		log.debug("Intercepting method: {}", context.getMethodName());

		var username = security.username();
		String userString = username.map(Object::toString).orElse(null);

		if (username.isPresent()) {
			log.debug("Username: {}", userString);
			Stream.of(context.getParameterValues())
				.filter(Auditable.class::isInstance)
				.map(Auditable.class::cast)
				.forEach(auditable -> auditable.setLastEditedBy(userString));
		}
		return context.proceed();
	}
}
















