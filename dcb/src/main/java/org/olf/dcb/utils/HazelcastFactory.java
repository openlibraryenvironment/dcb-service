package org.olf.dcb.utils;

import com.hazelcast.map.IMap;
import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Factory
public class HazelcastFactory {

	private static final Logger log = LoggerFactory.getLogger(HazelcastFactory.class);

	@Singleton
	@Named("hazelcastInstance")
	@Bean(preDestroy = "shutdown")
	public HazelcastInstance hazelcastInstance(Config config) {
		log.debug("creating hazelcast instance");
		return Hazelcast.newHazelcastInstance(config);
	}

}
