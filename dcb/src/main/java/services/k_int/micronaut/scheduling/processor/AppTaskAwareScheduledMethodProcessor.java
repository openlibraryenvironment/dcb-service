package services.k_int.micronaut.scheduling.processor;

import java.util.Optional;

import org.olf.dcb.core.AppConfig;
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
public class AppTaskAwareScheduledMethodProcessor extends ScheduledMethodProcessor {

	private static Logger log = LoggerFactory.getLogger(AppTaskAwareScheduledMethodProcessor.class);

	private final AppConfig config;

	public AppTaskAwareScheduledMethodProcessor(BeanContext beanContext, 
                Optional<ConversionService> conversionService,
                TaskExceptionHandler<?, ?> taskExceptionHandler, 
                AppConfig appConfig) {
		super(beanContext, conversionService, taskExceptionHandler);
		this.config = appConfig;
		log.info("Using AppTask aware scheduler");
	}

	@Override
	public void process(BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {

		if (!config.getScheduledTasks().isEnabled() && method.hasAnnotation(AppTask.class)) {
			log.info("Skipping task processing as {}.{} annotated as {} and scheduling is disabled in application config",
				method.getDeclaringType().getSimpleName(), method.getName(), AppTask.class.getSimpleName());
			return;
		}

		super.process(beanDefinition, method);
	}
}
