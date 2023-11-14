package org.olf.dcb.indexing.bulk;

import io.micronaut.core.annotation.Nullable;

public record IndexOperation<T, D> (
		Type type,
		T id,
		@Nullable D doc
		) {
	
	public IndexOperation(
			Type type,
			T id) {
		this(type, id , null);
	}
	
	public static enum Type {
		CREATE,
		UPDATE,
		DELETE
	}

	public static <T, D> IndexOperation<T, D> update( T docId, @Nullable D doc ) {
		return new IndexOperation<>(Type.UPDATE, docId, doc);
	}
	
	public static <T, D> IndexOperation<T, D> create( T docId, @Nullable D doc ) {
		return new IndexOperation<>(Type.CREATE, docId, doc);
	}
	
	public static <T, D> IndexOperation<T, D> delete( T docId ) {
		return new IndexOperation<>(Type.DELETE, docId);
	}
}
