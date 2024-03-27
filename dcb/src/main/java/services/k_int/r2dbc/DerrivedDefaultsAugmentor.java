package services.k_int.r2dbc;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.NonNull;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.Option;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Requires(bean = ConnectionFactoryOptions.class)
@Slf4j
@Singleton
public class DerrivedDefaultsAugmentor implements BeanCreatedEventListener<ConnectionFactoryOptions> {

	private static final int MIN_POOLSIZE = 4;
	private static final Option<Integer> OPT_MAX_SIZE = Option.valueOf("maxSize");
	private static final Option<Integer> OPT_INITIAL_SIZE = Option.valueOf("initialSize");

	@Value("${jvm.available-processors}")
	int availableProcessors;
	
	
	private int getRecommendedPoolsize() {
		if (availableProcessors < 1) {
			log.atWarn()
				.log("Could not determine number of available processors. Default database pool of [{}], will be used", MIN_POOLSIZE);
			
			return MIN_POOLSIZE;
		}
		
		// final int recommendation = (availableProcessors / 2) + 1;
		final int recommendation = (availableProcessors) + 1;
		if (recommendation < 4) {
			log.atWarn()
				.log("Based on the available proceesor information [{}], recommended pool size of [{}] would be used. "
						+ "A default of [{}] will be used instead as this is too low, and you should consider increasing the available cores", availableProcessors, recommendation, MIN_POOLSIZE);
			return MIN_POOLSIZE;
		}
		
		log.atInfo()
			.log("Based on the available proceesor information [{}], a pool size of [{}] will be used.", availableProcessors, recommendation);

		return recommendation;
	}

	@Override
	public ConnectionFactoryOptions onCreated(@NonNull BeanCreatedEvent<ConnectionFactoryOptions> event) {

		log.atInfo().log("event: {}",event);
		
		var opts = event.getBean();
		var newOptsBuilder = ConnectionFactoryOptions.builder().from(opts);
		
		final int recommendation = getRecommendedPoolsize();
		int initial = recommendation; // Default the initial poolsize to our recommendation.
		if (!opts.hasOption(OPT_MAX_SIZE)) {
			log.atWarn().log("Using recommended pool size {}",recommendation);
			newOptsBuilder.option(OPT_MAX_SIZE, recommendation);
		} else {
			var valObj = opts.getValue(OPT_MAX_SIZE);
			log.atWarn().log("Using specified pool size {}",valObj);
			try {
				if (valObj != null) {
					initial = Integer.valueOf("" + valObj);
				}
			} catch (NumberFormatException e) {
				log.error("Could not convert the supplied max pool size [{}], to an integer.", valObj);
			}
		}
	
		if (!opts.hasOption(OPT_INITIAL_SIZE)) {
			newOptsBuilder.option(OPT_INITIAL_SIZE, initial);
		}
		
		return newOptsBuilder.build();
	}
}
