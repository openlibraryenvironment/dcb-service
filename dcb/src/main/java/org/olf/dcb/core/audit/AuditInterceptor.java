package org.olf.dcb.core.audit;

import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.security.utils.SecurityService;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

//@Singleton
//@RequiredArgsConstructor
//public class AuditInterceptor implements MethodInterceptor<Object, Object> {
//
//	private static Logger log = LoggerFactory.getLogger(AuditInterceptor.class);
//	private final SecurityService security;
//
//	@Override
//	public Object intercept(MethodInvocationContext<Object, Object> context) {
//		log.debug("Intercepting method: {}", context.getMethodName());
//
//		PropagatedContext ctxt = PropagatedContext.get();
//		
//		security.username().ifPresent( userString -> {
//			log.debug("Username: {}", userString);
//			
//			Stream.of(context.getParameterValues())
//				.filter(val -> Auditable.class.isAssignableFrom( val.getClass() ))
//				.map(Auditable.class::cast)
//				.forEach(auditable -> auditable.setLastEditedBy(userString));
//		});
//		
//		return context.proceed();
//	}
//}
















