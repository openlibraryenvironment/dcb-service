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

		// If the task is an AppTask AND Enabled is set to false AND either the skipped list is NULL OR the skipped list contains this class
		if ( method.hasAnnotation(AppTask.class) && !config.getScheduledTasks().isEnabled() ) {
			log.info("Skipping task processing as {}.{} annotated as {} and scheduling is disabled in application config",
				method.getDeclaringType().getSimpleName(), method.getName(), AppTask.class.getSimpleName());
			return;
		}

		if ( config.getScheduledTasks().getSkipped().contains(method.getDeclaringType().getSimpleName()) ) {
			log.info("Skipping task processing as {}.{} annotated as {} as it explicitly skipped",
				method.getDeclaringType().getSimpleName(), method.getName(), AppTask.class.getSimpleName());
			return;
		}

		log.info("Continue to process {}.{} / {}",method.getDeclaringType().getSimpleName(), method.getName(), AppTask.class.getSimpleName());

		super.process(beanDefinition, method);
	}
}
