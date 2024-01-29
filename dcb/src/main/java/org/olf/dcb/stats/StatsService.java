package org.olf.dcb.stats;

import io.micronaut.runtime.context.scope.Refreshable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;

import org.olf.dcb.core.svc.BibRecordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.cluster.Member;
import com.hazelcast.map.IMap;
import jakarta.annotation.PreDestroy;

import io.micronaut.scheduling.annotation.Scheduled;


/**
 * The purpose of this class is to collect statistics across this instance of DCB. Devs should be
 * mindful that eventually DCB will be running in cluster mode with many instances so stats may need to be
 * aggregated downstream. This class should be a common point to collect/aggregate stats and metrics about the
 * running instance.
 *
 * Because many instances of DCB may be running, we store the stats in a distributed map.
 */
@Singleton
public class StatsService implements Runnable{

	private static final Logger log = LoggerFactory.getLogger(StatsService.class);
	private static final String HEARTBEAT_KEY = "lastHeartbeat";

  private final HazelcastInstance hazelcastInstance;


	public StatsService( HazelcastInstance hazelcastInstance ) {
		this.hazelcastInstance = hazelcastInstance;
	}

	// This needs to be a hazelcast map ideally
	Map<String, StatCounter> stat_counters = new HashMap<String, StatCounter>();
  // private IMap<String, StatCounter> stat_counters = null;


  @jakarta.annotation.PostConstruct
  public void init() {
    stat_counters = new HashMap(); // hazelcastInstance.getMap("stats");
  }

	private StatCounter getCounter(String id) {
		StatCounter result = stat_counters.get(id);
		if ( result == null ) {
			result = new StatCounter(id);
			stat_counters.put(id,result);
		}
		return result;
	}
	/**
	 * StatsService offers a general mechanism for maintaining a histogram of event counts. Initially, the
	 * aim is to provide per-minute counts for the last hour, per hour counts for the last day and per day counts
	 * for the past 30 days.
	 * An example event BibRecordInserted, Context DCB (In case we want to be more granular later)
	 */
	public void notifyEvent(String event, String context) {
		StatCounter sc = getCounter(event+":"+context);
		synchronized (sc) {
			sc.increment();
		}
	}

	public Report getReport() {
		// log.debug("report {}",stat_counters);
		return new Report(stat_counters);
	}

	public void notifyTimedEvent(String event, String context, long elapsed) {
		// log.debug("notifyTimedEvent({},{},{})",event,context,elapsed);
	}

	@Serdeable
	public class Report {

		public Map<String, StatCounter.StatCounterReport> counters;

		public Report(Map<String, StatCounter> internal_counters) {
			// this.counters = counters;
			this.counters = new HashMap<String, StatCounter.StatCounterReport>();
			for (var entry : internal_counters.entrySet()) {
				// log.debug("Adding report for {}",entry.getKey());
				counters.put(entry.getKey(), entry.getValue().report());
			}
		}

		public Map<String, StatCounter.StatCounterReport> getCounters() {
			return counters;
		}
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
					log.debug("Eviction testing {} / {}",entry.getKey(), entry.getValue() );
					if ( ! isLive(entry.getKey() ) ) {
						evict_list.add(entry.getKey());
					}
				}

				if ( evict_list.size() > 0 )	 {
					log.debug("Evicting zombie nodes: {}",evict_list);
					for ( var k : evict_list )
						dcbNodeInfo.remove(k);
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
		log.debug("test liveliness {}",nodeid);
		boolean result = false;
		for ( var m : hazelcastInstance.getCluster().getMembers() ) {
			String member_id_as_string = m.getUuid().toString();
			log.debug("Test {} == {} - {}",nodeid,member_id_as_string,nodeid.equals(member_id_as_string));
			if ( nodeid.equals(member_id_as_string) )
				result = true;
		}
		return result;
	}

}
