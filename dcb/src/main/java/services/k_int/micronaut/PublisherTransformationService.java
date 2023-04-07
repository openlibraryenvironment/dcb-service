package services.k_int.micronaut;

import java.util.function.Function;

import org.reactivestreams.Publisher;

import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Singleton
public class PublisherTransformationService {

	private final BeanContext beanContext;

        private static Logger log = LoggerFactory.getLogger(PublisherTransformationService.class);

	public PublisherTransformationService( @NonNull BeanContext beanContext ) {
		this.beanContext = beanContext;
	}
	
	@NonNull
	public<T> Publisher<T> applyTransformations ( @NonNull String type, @NonNull Publisher<T> pub ) {
		Function<Publisher<T>, Publisher<T>> chain = getTransformationChain ( type );
		return chain.apply(pub);
	}
	
	@SuppressWarnings("unchecked")
	@NonNull
	public<T> Function<Publisher<T>, Publisher<T>> getTransformationChain ( String type ) {
		
		var hooks = beanContext.getBeansOfType( PublisherTransformation.class, Qualifiers.byName(type) );
		Function<Publisher<T>, Publisher<T>> chain = ( pub ) -> pub;
		for (var hook : hooks) {
			log.info("Adding publisher transformation {} : {}",type,hook.getClass().getName());
			chain = chain.andThen(hook::apply);
		}
		
		return chain::apply;
	}
}
