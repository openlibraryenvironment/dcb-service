package org.olf.dcb;

import java.util.List;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.test.annotation.TransactionMode;
import io.micronaut.test.context.TestExecutionListener;
import io.micronaut.test.extensions.AbstractMicronautExtension;
import io.micronaut.transaction.TransactionOperations;
import io.micronaut.transaction.sync.SynchronousTransactionOperationsFromReactiveTransactionOperations;
import io.micronaut.transaction.test.DefaultTestTransactionExecutionListener;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@EachBean(TransactionOperations.class)
@Requires(classes = TestExecutionListener.class)
@Requires(property = AbstractMicronautExtension.TEST_TRANSACTIONAL, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
@Replaces(DefaultTestTransactionExecutionListener.class)
public class DcbTestTransactionExecutionListener extends DefaultTestTransactionExecutionListener {

	private static TransactionOperations<Object> preferSynchronousFromReactiveTransactionManager(List<TransactionOperations<Object>> transactionManagers) {
		final int candidates = transactionManagers.size();
		
		if ( candidates == 0 ) return null;
		if ( candidates == 1 ) return transactionManagers.get(0);
		
		TransactionOperations<Object> elected = transactionManagers.stream()
			.filter( manager -> SynchronousTransactionOperationsFromReactiveTransactionOperations.class.isAssignableFrom(manager.getClass()))
			.findFirst()
			.orElse(transactionManagers.get(0));
		
		log.info( "Elected {} as transaction manager from {} candidates", elected.getClass().getSimpleName(), candidates );
		return elected;
	}
	
	public DcbTestTransactionExecutionListener(
			List<TransactionOperations<Object>> transactionManagers,
			boolean rollback,
			TransactionMode transactionMode) {
		super(preferSynchronousFromReactiveTransactionManager(transactionManagers), rollback, transactionMode);
	}

}
