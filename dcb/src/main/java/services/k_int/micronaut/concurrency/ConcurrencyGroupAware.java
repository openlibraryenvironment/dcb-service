package services.k_int.micronaut.concurrency;

public interface ConcurrencyGroupAware {
	default String getConcurrencyGroupKey() {
		return ConcurrencyGroup.DEFAULT_GROUP_KEY;
	}
}
