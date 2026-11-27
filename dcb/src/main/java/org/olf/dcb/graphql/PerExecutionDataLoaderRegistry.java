package org.olf.dcb.graphql;

import static graphql.execution.instrumentation.dataloader.EmptyDataLoaderRegistryInstance.EMPTY_DATALOADER_REGISTRY;

import org.dataloader.DataLoaderRegistry;

import graphql.ExecutionInput;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;

/**
 * Gives every execution a DataLoaderRegistry of its own, so a fetcher can register a loader in it.
 * Without one graphql-java uses a shared empty registry that refuses registration and dispatches
 * nothing, and micronaut-graphql attaches one only when the application defines a registry bean.
 * Doing it here covers every caller of the GraphQL bean, the HTTP endpoint and tests alike.
 */
public class PerExecutionDataLoaderRegistry implements Instrumentation {
	@Override
	public ExecutionInput instrumentExecutionInput(ExecutionInput executionInput,
		InstrumentationExecutionParameters parameters, InstrumentationState state) {

		return executionInput.getDataLoaderRegistry() == EMPTY_DATALOADER_REGISTRY
			? executionInput.transform(builder -> builder.dataLoaderRegistry(new DataLoaderRegistry()))
			: executionInput;
	}
}
