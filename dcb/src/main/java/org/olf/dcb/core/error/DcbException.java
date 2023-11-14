package org.olf.dcb.core.error;

import java.util.Objects;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

public class DcbException extends RuntimeException {

	@java.io.Serial
	private static final long serialVersionUID = -5407814205815019035L;

	protected DcbException(@NonNull String message, @Nullable Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public DcbException( @NonNull String message, @Nullable Throwable cause) {
		this(message, cause, true, true);
	}

	public DcbException( @NonNull String message) {
		this(message, null);
	}

	public DcbException( @NonNull Throwable cause) {
		this( Objects.requireNonNullElse(cause.getMessage(), cause.toString()), cause );
	}
}