package org.olf.dcb.core.error;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

public class DcbError extends DcbException {

	@java.io.Serial
	private static final long serialVersionUID = 3981433345313620053L;
	
	private static Logger log = LoggerFactory.getLogger(DcbError.class);
	
	private DcbError( @NonNull String message, @Nullable Throwable cause, boolean enableSuppression) {
		super(message, cause, enableSuppression, log.isDebugEnabled());
	}

	public DcbError( @NonNull String message, @Nullable Throwable cause ) {
		this(message, cause, true);
	}

	public DcbError( @NonNull String message ) {
		this(message, null);
	}

	public DcbError( @NonNull Throwable cause ) {
		this( Objects.requireNonNullElse(cause.getMessage(), cause.toString()) , cause );
	}
}
