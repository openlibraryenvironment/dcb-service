package org.olf.dcb.utils;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;

@Factory
public class HazelcastConfiguration {

    @Bean
    public Config hazelcastConfig() {
        Config configuration = new Config().setClusterName("dcb-service-cluster");
				// Use Environment variables to set up.. E.G. For K-int deployments on K8s
				// set HZ_NETWORK_JOIN_MULTICAST_ENABLED=false
				// set HZ_NETWORK_KUBERNETES_ENABLED=true
        // JoinConfig joinConfig = configuration.getNetworkConfig().getJoin();
        // joinConfig.getMulticastConfig().setEnabled(false);
        // joinConfig.getTcpIpConfig().setEnabled(true).addMember("localhost");
        return configuration;
    }
}
