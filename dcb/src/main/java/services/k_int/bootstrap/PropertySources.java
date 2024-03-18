package services.k_int.bootstrap;

import java.util.List;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.env.BootstrapPropertySourceLocator;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.exceptions.ConfigurationException;
import jakarta.inject.Singleton;

@BootstrapContextCompatible
@Singleton
public class PropertySources implements BootstrapPropertySourceLocator {

	@Override
	public Iterable<PropertySource> findPropertySources(Environment environment) throws ConfigurationException {
		return List.of(SystemInformationSource.get());
	}
	
}
