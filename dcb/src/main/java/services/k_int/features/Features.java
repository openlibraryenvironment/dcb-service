package services.k_int.features;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Context;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Context
@Singleton
@BootstrapContextCompatible
@ConfigurationProperties(value="features")
public class Features {
	
	static final Pattern REGEX_VALID_FEATURE_NAME = Pattern.compile("^[a-zA-Z][\\w-]+$");
	
	private final Map<String, Boolean> knownStates = new HashMap<>();
	
	public Features() {
		
		_singleton = this;
	}
	
	void setEnabled( List<String> enabled ) {
		enabled.stream()
			.map( this::validateName )
			.forEach( featureName -> knownStates.put(featureName, Boolean.TRUE) );
	}
	
	private String validateName( String name ) {
		
		log.info("ENABLED FEATURE: [{}]", name);
		
		if (REGEX_VALID_FEATURE_NAME.matcher(name).matches()) return name;
		
		throw new IllegalArgumentException(("Invalid feature name [%s]. "
				+ "Feature name must only contain Alphanumeric characters and '-' or '_'. The first chaaracter must also be alphabetic.").formatted(name));
	}
	
	private static Features _singleton; 
	
	public static boolean featureIsEnabled( String featureName ) {
		return _singleton.isEnabled(featureName);
	}
	
	public boolean isEnabled( String featureName ) {
		return Optional.ofNullable( knownStates.get(featureName) )
			.orElse(Boolean.FALSE);
	}
}
