package services.k_int.micronaut.scheduling.processor;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.scheduling.TaskExceptionHandler;
import io.micronaut.scheduling.processor.ScheduledMethodProcessor;
import jakarta.inject.Singleton;

@Singleton
@Replaces(bean = ScheduledMethodProcessor.class)
public class ConfigurableScheduledMethodProcessor extends ScheduledMethodProcessor {

	private static Logger log = LoggerFactory.getLogger(ConfigurableScheduledMethodProcessor.class);
	
	public ConfigurableScheduledMethodProcessor(BeanContext beanContext, Optional<ConversionService<?>> conversionService,
			TaskExceptionHandler<?, ?> taskExceptionHandler) {
		super(beanContext, conversionService, taskExceptionHandler);
	}
	
	@Override
	public void process(BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
		log.info("Using overridden scheduler");
		super.process(beanDefinition, method);
	}
}
