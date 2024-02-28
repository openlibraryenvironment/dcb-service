package services.k_int.hazelcast.federation;

import java.util.Optional;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.env.Environment;
import io.micronaut.core.io.ResourceResolver;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Factory
@Slf4j
public class HazelcastProvidersFactory {
	private static final String EXTENSION_YML = "yml";
	private static final String EXTENSION_YAML = "yaml";
	private final Environment env;
	private final ResourceResolver resourceResolver;

	public HazelcastProvidersFactory(Environment env, ResourceResolver resourceResolver) {
		this.env = env;
		this.resourceResolver = resourceResolver;
	}

	private Optional<Config> findHazelcastConfig(String fileName) {
		return resourceResolver.getResourceAsStream("classpath:" + fileName + "." + EXTENSION_YML)
				.or(() -> resourceResolver.getResourceAsStream("classpath:" + fileName + "." + EXTENSION_YAML))
				.map(inputStream -> Config.loadFromStream(inputStream));
	}

	private boolean environemntSpecificConfigCandidate(String envName) {

		return switch (envName) {
		case Environment.KUBERNETES, Environment.DEVELOPMENT -> true;

		default -> false;
		};

	}

	private Optional<Config> applicableHazelcastConfig() {
		return env.getActiveNames().stream()
				.filter(this::environemntSpecificConfigCandidate)
				.map("hazelcast-%s"::formatted)
				.map(this::findHazelcastConfig)
				.filter(Optional::isPresent)
				.map(Optional::get).findFirst();
	}

	@Singleton
	@Named("hazelcastInstance")
	@Bean(preDestroy = "shutdown")
	public HazelcastInstance hazelcastInstance() {
		log.debug("creating hazelcast instance");
		return applicableHazelcastConfig().map(Hazelcast::newHazelcastInstance).orElseGet(Hazelcast::newHazelcastInstance);
	}
}
