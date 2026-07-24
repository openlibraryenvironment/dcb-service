// NB: this class deliberately lives in io.micronaut.scheduling.processor rather than
// in services.k_int.*, and it must stay here.
//
// ScheduledMethodProcessor declares a PACKAGE-PRIVATE @EventListener method:
//
//     void scheduleTasks(StartupEvent)
//
// An @EventListener is @Executable. If this subclass sits in any other package that
// inherited method is not accessible from the subclass's package, and the Micronaut
// annotation processor fails the build with:
//
//     error: Method annotated as executable but is declared private.
//            To invoke the method using reflection annotate it with @ReflectiveAccess
//
// That error carries NO file or line, because the offending element is inherited
// rather than declared in application source -- which makes it extremely hard to
// trace back here. Worse, it aborts annotation processing before javac finishes
// type-checking, masking every other compile error beneath it.
//
// Annotating the class @ReflectiveAccess silences that error without moving it, but
// trades one problem for another: Micronaut then emits a reflective
//
//     $AppTaskAwareScheduledMethodProcessor$ApplicationEventListener$scheduleTasks1$Intercepted$Definition
//
// for an INHERITED method, whose originating element is the superclass in a jar
// rather than a file in this source tree. Gradle's incremental compiler cannot
// associate that generated class with any source file, so it never deletes the stale
// copy, and the next incremental compile dies with:
//
//     error: Unexpected error: Attempt to recreate a file for type ...
//
// which clean builds hide and only local incremental builds hit. Keeping the class in
// this package avoids both failures. Do not "tidy" it into a services.k_int package.
package io.micronaut.scheduling.processor;

import org.olf.dcb.core.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.scheduling.TaskExceptionHandler;
import jakarta.inject.Singleton;
import services.k_int.micronaut.scheduling.processor.AppTask;

@Singleton
@Replaces(bean = ScheduledMethodProcessor.class)
public class AppTaskAwareScheduledMethodProcessor extends ScheduledMethodProcessor {

	private static Logger log = LoggerFactory.getLogger(AppTaskAwareScheduledMethodProcessor.class);

	private final AppConfig config;

	// NB: Micronaut 5 changed this constructor's second parameter from
	// Optional<ConversionService> to ConversionService.
	public AppTaskAwareScheduledMethodProcessor(BeanContext beanContext,
	                ConversionService conversionService,
	                TaskExceptionHandler<?, ?> taskExceptionHandler,
	                AppConfig appConfig) {
		super(beanContext, conversionService, taskExceptionHandler);
		this.config = appConfig;
		log.info("Using AppTask aware scheduler");
	}

	// Redeclares the inherited @EventListener so the generated listener definition
	// originates from this source file rather than from the superclass in a jar.
	@Override
	@EventListener
	void scheduleTasks(StartupEvent event) {
		super.scheduleTasks(event);
	}

	@Override
	public <B> void process(BeanDefinition<B> beanDefinition, ExecutableMethod<B, ?> method) {

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

		log.info("Continue to process {}(.{}) / {}",method.getDeclaringType().getSimpleName(), method.getName(), AppTask.class.getSimpleName());

		super.process(beanDefinition, method);
	}
}
