package org.olf.dcb.security;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.token.validator.TokenValidator;
import reactor.core.publisher.Mono;

@Requires(env = Environment.TEST )
@Context
public class TestStaticTokenValidator<T> implements TokenValidator<T> {

	private static final Logger log = LoggerFactory.getLogger(TestStaticTokenValidator.class);
  private static final Map<String, Authentication> validTokens = new ConcurrentHashMap<>();
  
  public TestStaticTokenValidator(Environment env) {
		if (!env.getActiveNames().contains(Environment.TEST)) {
			throw new IllegalStateException("TestStaticTokenValidator is not secure and should not be loaded outside a Test or Development scope");
		}
		
		log.warn("*** WARNING *** TestStaticTokenValidator loaded. This is not a secure Token Validator");
	}

	@NonNull
	public static Authentication add(@NonNull String token, @NonNull String username) {
		return add(token, username, null, null);
	}

	@NonNull
	public static Authentication add(@NonNull String token, @NonNull String username, @NonNull Collection<String> roles) {
		return add(token, username, roles, null);
	}

	@NonNull
	public static Authentication add(@NonNull String token, @NonNull String username, @NonNull Map<String, Object> attributes) {
		return add(token, username, null, attributes);
	}

	@NonNull
	public static Authentication add(@NonNull String token, @NonNull String username, @Nullable Collection<String> roles,
			@Nullable Map<String, Object> attributes) {
		
		if (validTokens.containsKey(token)) {
			throw new IllegalStateException("Static test token [%s] already added. Either call invalidate first or use a different token value."
				.formatted(token));
		}
		
		// Add new token.
		var auth = Authentication.build(username, roles, attributes);
		validTokens.put(token, auth);
		return auth;
	}
	
	@Nullable
	public static Authentication invalidateToken(@NonNull String token) {
		return validTokens.remove(token);
	}

	@Override
	public Publisher<Authentication> validateToken(String token, @Nullable Object request) {
		var auth = validTokens.get(token);
		
		if (auth != null ) log.info("Static token {} matched to Token[{}] with Roles[{}] Attr[{}]", token, auth.getName(), auth.getRoles(), auth.getAttributes());
		
  	return Mono.justOrEmpty(auth);
	}  		
}
