package org.olf.dcb.utils;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Factory
public class HazelcastConfiguration {

	private static final Logger log = LoggerFactory.getLogger(HazelcastConfiguration.class);


	@Bean
	public Config hazelcastConfig() {
		// https://github.com/micronaut-projects/micronaut-cache/blob/master/cache-hazelcast/src/main/java/io/micronaut/cache/hazelcast/HazelcastFactory.java
		// Election example : https://medium.com/microservices-architecture/leader-election-using-hazelcast-b7fddd70bc0e
		// https://docs.hazelcast.com/tutorials/caching-micronaut
		Config configuration = new Config().setClusterName("dcb-service-cluster");
		// Use Environment variables to set up.. E.G. For K-int deployments on K8s
		// set HZ_NETWORK_JOIN_MULTICAST_ENABLED=false
		// set HZ_NETWORK_JOIN_KUBERNETES_ENABLED=true
		// JoinConfig joinConfig = configuration.getNetworkConfig().getJoin();
		// joinConfig.getMulticastConfig().setEnabled(false);
		// joinConfig.getTcpIpConfig().setEnabled(true).addMember("localhost");
		return configuration;
	}
}
