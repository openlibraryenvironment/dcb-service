package org.olf.dcb.core.security;

import static io.micronaut.security.rules.SecurityRuleResult.ALLOWED;
import static io.micronaut.security.rules.SecurityRuleResult.REJECTED;

import java.util.Collections;
import java.util.List;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.management.endpoint.EndpointSensitivityProcessor;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.authentication.AuthenticationMode;
import io.micronaut.security.config.InterceptUrlMapPattern;
import io.micronaut.security.config.SecurityConfiguration;
import io.micronaut.security.rules.IpPatternsRule;
import io.micronaut.security.rules.SecurityRuleResult;
import io.micronaut.security.rules.SensitiveEndpointRule;
import io.micronaut.security.token.RolesFinder;
import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.Setter;
import reactor.core.publisher.Mono;
import services.k_int.utils.TupleUtils;

@Singleton
@Replaces(SensitiveEndpointRule.class)
@ConfigurationProperties(DcbSensitiveEndpointRule.PREFIX)
@Setter
@Getter
public class DcbSensitiveEndpointRule extends SensitiveEndpointRule {
	
	private static final Logger log = LoggerFactory.getLogger(DcbSensitiveEndpointRule.class);
	
	protected static final String PREFIX = "dcb.security.internal-endpoints";
	
	public static final String DEFAULT_SYSTEM_ROLE = "ROLE_SYSTEM";
	
  private List<String> allowedRoles = Collections.singletonList(DEFAULT_SYSTEM_ROLE);
	
	private IpPatternsRule ipPatternsRule;
	private final RolesFinder rolesFinder;

	private List<String> ipPatterns;
	
	// Bind ip[s from different list 
	public DcbSensitiveEndpointRule(EndpointSensitivityProcessor endpointSensitivityProcessor, RolesFinder rolesFinder) {
		super(endpointSensitivityProcessor);
		this.rolesFinder = rolesFinder;
	}

	@Override
	@NonNull
	protected Publisher<SecurityRuleResult> checkSensitiveAnonymous(@NonNull HttpRequest<?> request, @NonNull ExecutableMethod<?, ?> method) {
		return checkIpOrRole(request, null, method);
	}
	
	@Override
	@NonNull
	protected Publisher<SecurityRuleResult> checkSensitiveAuthenticated(@NonNull HttpRequest<?> request,
			@NonNull Authentication authentication, @NonNull ExecutableMethod<?, ?> method) {
		return checkIpOrRole(request, authentication, method);
	}
	
	private Publisher<SecurityRuleResult> checkIpOrRole (@NonNull HttpRequest<?> request,
			@Nullable Authentication authentication, @NonNull ExecutableMethod<?, ?> method) {
			// Ip rules only reject elements not in the list, and allow the rest of the chain to continue.
			// We want to essentially reverse this behaviour, and ALLOW immediately if the ipRule returns "unknown"
			// and then check the roles instead.
			return Mono
				.justOrEmpty( ipPatternsRule )
				.flatMap( rule -> Mono.from( rule.check(request, authentication) ))
				.defaultIfEmpty( REJECTED ) // No IP list we assume rejected here.
				.flatMap( TupleUtils.curry(authentication, this::handleReturnFromIpRule) );
	}
	
	private Mono<SecurityRuleResult> handleReturnFromIpRule(@Nullable Authentication authentication, SecurityRuleResult ipResult) {
		
		return switch ( ipResult ) {
		
			case REJECTED -> {
				boolean hasRequiredRole = authentication != null && rolesFinder.hasAnyRequiredRoles(allowedRoles, authentication.getRoles());
				
				if (hasRequiredRole) {
					log.debug("Sensitive endpoint auth allowed based on role allocation");
					yield Mono.just( ALLOWED );
				}
				
				log.debug("Sensitive endpoint auth rejected");
				yield Mono.just( REJECTED );
			}
			
			default -> {
				// Allowed currently not returned by the rule, but if it was it should propagate.
				log.debug("Sensitive endpoint auth allowed based on IP address");
				yield Mono.just( ALLOWED );
			}
		};
	}

	public void setIpPatterns(List<String> ipPatterns) {
		this.ipPatterns = ipPatterns;

		// Null out if empty list or null.
		if (ipPatterns == null || ipPatterns.isEmpty()) {
			this.ipPatternsRule = null;
			return;
		}

		// When the patterns change (usually only once on config binding)
		// Use anonymous inner class as a way of not adding another implementation
		// to Micronaut's bean context.
		this.ipPatternsRule = new IpPatternsRule(rolesFinder, new SecurityConfiguration() {
			
			@Override
			public @Nullable AuthenticationMode getAuthentication() { throw new UnsupportedOperationException(); }
			
			@Override
			public boolean isInterceptUrlMapPrependPatternWithContextPath() { throw new UnsupportedOperationException(); }
			
			@Override
			public List<InterceptUrlMapPattern> getInterceptUrlMap()  { throw new UnsupportedOperationException(); }
			
			@Override
			public List<String> getIpPatterns() { return ipPatterns; }
		});
	}
}
