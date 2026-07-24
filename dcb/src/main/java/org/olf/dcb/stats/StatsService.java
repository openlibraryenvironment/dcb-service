package org.olf.dcb.stats;

import jakarta.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;


import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.cluster.Member;
import com.hazelcast.map.IMap;

import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.scheduling.annotation.Scheduled;


/**
 * The purpose of this class is to collect statistics across this instance of DCB. Devs should be
 * mindful that eventually DCB will be running in cluster mode with many instances so stats may need to be
 * aggregated downstream. This class should be a common point to collect/aggregate stats and metrics about the
 * running instance.
 *
 * Because many instances of DCB may be running, we store the stats in a distributed map.
// */
@Singleton
public class StatsService implements Runnable{

	private static final Logger log = LoggerFactory.getLogger(StatsService.class);
	private static final String HEARTBEAT_KEY = "lastHeartbeat";

	private static final String EVENTS_METRIC = "dcb.stats.events";
	private static final String TIMED_METRIC = "dcb.stats.timed";

  private final HazelcastInstance hazelcastInstance;
	private final MeterRegistry meterRegistry;

	public StatsService(HazelcastInstance hazelcastInstance, MeterRegistry meterRegistry) {
		this.hazelcastInstance = hazelcastInstance;
		this.meterRegistry = meterRegistry;
	}

	/**
	 * Records an event count by (event, context) as a Micrometer counter — scrape it
	 * at the Prometheus/metrics endpoint. Replaces the former unbounded in-heap
	 * stat_counters map (which had no live reader and grew without bound).
	 *
	 * Both tags are low-cardinality: event is a fixed set (BibInsert, BibUpdate,
	 * DroppedTitle, DroppedNullTitle, IngestRecord) and context is a configured Host
	 * LMS source code. Never pass a UUID, timestamp, or raw user input here — that
	 * explodes meter cardinality (the high-cardinality ban).
	 */
	public void notifyEvent(String event, String context) {
		meterRegistry.counter(EVENTS_METRIC, "event", event, "context", context).increment();
	}

	public void notifyTimedEvent(String event, String context, long elapsed) {
		meterRegistry.timer(TIMED_METRIC, "event", event, "context", context)
			.record(elapsed, TimeUnit.MILLISECONDS);
	}

	/**
	 * Periodic tasks to publish stats about this node so we can get a view over the whole cluster
	 * from one place;
	 */
  @Override
  @Scheduled(initialDelay = "2m", fixedDelay = "${dcb.stats.interval:5m}")
  public void run() {
    log.debug("DCB Stats Service run");
    String thisNodeUUID = hazelcastInstance.getCluster().getLocalMember().getUuid().toString();
		long now = System.currentTimeMillis();
    try {
      IMap<String,Map<String,String>> dcbNodeInfo = hazelcastInstance.getMap("DCBNodes");
			Map<String,String> nodeInfo = dcbNodeInfo.get(thisNodeUUID);
      nodeInfo.put(HEARTBEAT_KEY, String.valueOf(now));
      dcbNodeInfo.put(thisNodeUUID, nodeInfo);


			if ( amITheLeader() ) {
				List evict_list = new ArrayList();
				for ( var entry : dcbNodeInfo.entrySet() ) {
					// log.debug("Eviction testing {} / {}",entry.getKey(), entry.getValue() );
					if ( ! isLive(entry.getKey() ) ) {
						evict_list.add(entry.getKey());
					}
				}

				if ( evict_list.size() > 0 )	 {
					for ( var k : evict_list ) {
					  log.debug("HZ Evicting zombie nodes: {}",k);
						dcbNodeInfo.remove(k);
					}
				}
			}
    }
    catch ( Exception e ) {
      log.error("problem",e);
    }
	}

	public boolean amITheLeader() {
		Member oldestMember = hazelcastInstance.getCluster().getMembers().iterator().next();
		return oldestMember.localMember();
	}

	public boolean isLive(String nodeid) {
		// log.debug("test liveliness {}",nodeid);
		boolean result = false;
		for ( var m : hazelcastInstance.getCluster().getMembers() ) {
			String member_id_as_string = m.getUuid().toString();
			// log.debug("Test {} == {} - {}",nodeid,member_id_as_string,nodeid.equals(member_id_as_string));
			if ( nodeid.equals(member_id_as_string) )
				result = true;
		}
		return result;
	}

}
