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
	private final String FILENAME_PREFIX = "hazelcast";
	private static final String EXTENSION_YML = "yml";
	private static final String EXTENSION_YAML = "yaml";
	private final Environment env;
	private final ResourceResolver resourceResolver;

	public HazelcastProvidersFactory(Environment env, ResourceResolver resourceResolver) {
		this.env = env;
		this.resourceResolver = resourceResolver;
	}

	private Optional<Config> findHazelcastConfig(String fileName) {
		
		log.info("Looking for hazelcast config [{}]", fileName);
		return resourceResolver.getResourceAsStream("classpath:" + fileName + "." + EXTENSION_YML)
				.or(() -> resourceResolver.getResourceAsStream("classpath:" + fileName + "." + EXTENSION_YAML))
				.map(Config::loadFromStream);
	}

	private boolean environmentSpecificConfigCandidate(String envName) {

		return switch (envName) {
		case Environment.KUBERNETES, Environment.DEVELOPMENT -> true;

		default -> false;
		};

	}

	private Optional<Config> applicableHazelcastConfig() {
		return env.getActiveNames().stream()
				.filter(this::environmentSpecificConfigCandidate)
				.map((FILENAME_PREFIX + "-%s")::formatted)
				.map(this::findHazelcastConfig)
				.flatMap(Optional::stream)
				.findFirst()
				.or(() -> findHazelcastConfig(FILENAME_PREFIX) );
	}

	@Singleton
	@Named("hazelcastInstance")
	@Bean(preDestroy = "shutdown")
	public HazelcastInstance hazelcastInstance() {
		
		return applicableHazelcastConfig()
			.map(Hazelcast::newHazelcastInstance)
			.get();
	}
}
