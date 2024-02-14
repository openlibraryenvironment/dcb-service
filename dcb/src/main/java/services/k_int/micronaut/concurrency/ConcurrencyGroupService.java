package services.k_int.micronaut.concurrency;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.core.annotation.NonNull;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;

@Singleton
public class ConcurrencyGroupService {
	
	private final Map<String, ConcurrencyGroup> allGroups;
	
	private static final Logger log = LoggerFactory.getLogger(ConcurrencyGroupService.class);
	
	public ConcurrencyGroupService(ConcurrencyGroup[] groups) {
		
		Map<String, ConcurrencyGroup> groupMap = new HashMap<>();
		groupMap.put(ConcurrencyGroup.DEFAULT_GROUP_KEY, ConcurrencyGroup.DEFAULT_GROUP);
		
		for (var group : groups) {
			groupMap.put(group.getName(), group);
		}
		
		allGroups = Collections.unmodifiableMap(groupMap);
		log.info("Created concurrency groups: {}", allGroups);
	}
	
	private static String getKeyFromConcurrencyGroupAware( @NonNull ConcurrencyGroupAware groupAware ) {
		return groupAware.getConcurrencyGroupKey();
	}
	
	private Integer getConcurrencyLimitFromConcurrencyGroupAware( @NonNull String groupKey ) {
		return Objects.requireNonNullElseGet(allGroups.get(groupKey), () -> {
				log.warn("Concurrency group key [{}] requested but hasn't been configured. Default to using [{}]", groupKey, ConcurrencyGroup.DEFAULT_GROUP_KEY);
				return allGroups.get(ConcurrencyGroup.DEFAULT_GROUP_KEY);
			}).getLimit();
		
	}
	
	public <R, T extends ConcurrencyGroupAware> Function<Publisher<T>, Flux<R>> toGroupedSubscription( Function<T, Publisher<R>> getPublisher ) {
		return ( Publisher<T> allPublishers ) -> getConcurrencyGroupAwareSubscription( allPublishers, getPublisher );
	}
	
	public <R, T extends ConcurrencyGroupAware> Flux<R> getConcurrencyGroupAwareSubscription(Publisher<T> allPublishers, Function<T, Publisher<R>> getPublisher) {
		return this.getGroupedSubscription(allPublishers, ConcurrencyGroupService::getKeyFromConcurrencyGroupAware, this::getConcurrencyLimitFromConcurrencyGroupAware, getPublisher);
	}
	
	private <T> Publisher<T> addLoggers( String group, Publisher<T> pub ) {
		return Flux.from(pub)
			.doOnSubscribe(subscriptionStartLogger( group ))
			.doOnTerminate(subscriptionEndLogger( group ));
	}
	
	private <T, R> Function<Flux<T>, Publisher<R>> doMapping(Function<T, Publisher<R>> getPublisher, int limit, String groupName) {
		return sourcePub -> {
			if (limit < 1) {
				return sourcePub.flatMap(source -> addLoggers( groupName, getPublisher.apply(source)));
			}			
		  return sourcePub.flatMap(source -> addLoggers( groupName, getPublisher.apply(source)), limit);
		};
	}
	
	private Runnable subscriptionEndLogger( String group ) {
		return () -> log.debug("Subscription terminated for group {}", group);
	}
	

	private Consumer<Subscription> subscriptionStartLogger( String group ) {
		return _sub -> log.debug("Starting subscription using {} for group {}", _sub.getClass(), group);
	}
	
	public <T, R> Flux<R> getGroupedSubscription(Publisher<T> allPublishers, Function<T, String> getGroupKey, Function<String, Integer> groupKeyToLimit, Function<T, Publisher<R>> getPublisher) {
		
		return Flux.from(allPublishers)
			.collectMultimap(getGroupKey)
			.flux()
			.flatMap( sources -> {
				if (sources.size() < 1) {
					return Flux.empty();
				}
				
				// Go through all groups and get the source and max limits.
				var groups = sources.keySet().iterator();
				
				// First group.
				var group = groups.next();
				
				int limit = groupKeyToLimit.apply( group );
				
				var flux = Flux.fromIterable(sources.get(group))
						.transform( this.doMapping(getPublisher, limit, group));
				
				// Any more groups.
				while (groups.hasNext()) {
					group = groups.next();
					limit = groupKeyToLimit.apply( group );
					flux = flux.mergeWith(Flux.fromIterable(sources.get(group))
						.transform( this.doMapping(getPublisher, limit, group) ));
				}
				
				return flux;
			});
	}
}
