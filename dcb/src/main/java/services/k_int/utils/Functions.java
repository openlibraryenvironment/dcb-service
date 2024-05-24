package services.k_int.utils;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import reactor.function.Consumer3;
import reactor.function.Consumer4;
import reactor.function.Consumer5;
import reactor.function.Function3;
import reactor.function.Function4;
import reactor.function.Function5;

public interface Functions {
	public static <T1, T2, R> Function<T2, R> curry(T1 t1, BiFunction<T1, T2, R> function) {
		return t2 -> function.apply(t1, t2);
	}

	public static <T1, T2, T3, R> BiFunction<T2, T3, R> curry(T1 t1, Function3<T1, T2, T3, R> function) {
		return (t2, t3) -> function.apply(t1, t2, t3);
	}

	public static <T1, T2, T3, R> Function<T3, R> curry(T1 t1, T2 t2, Function3<T1, T2, T3, R> function) {
		return (t3) -> function.apply(t1, t2, t3);
	}

	public static <T1, T2, T3, T4, R> Function3<T2, T3, T4, R> curry(T1 t1, Function4<T1, T2, T3, T4, R> function) {
		return (t2, t3, t4) -> function.apply(t1, t2, t3, t4);
	}

	public static <T1, T2, T3, T4, R> BiFunction<T3, T4, R> curry(T1 t1, T2 t2, Function4<T1, T2, T3, T4, R> function) {
		return (t3, t4) -> function.apply(t1, t2, t3, t4);
	}

	public static <T1, T2, T3, T4, R> Function<T4, R> curry(T1 t1, T2 t2, T3 t3, Function4<T1, T2, T3, T4, R> function) {
		return (t4) -> function.apply(t1, t2, t3, t4);
	}

	public static <T1, T2, T3, T4, T5, R> Function4<T2, T3, T4, T5, R> curry(T1 t1,
			Function5<T1, T2, T3, T4, T5, R> function) {
		return (t2, t3, t4, t5) -> function.apply(t1, t2, t3, t4, t5);
	}

	public static <T1, T2, T3, T4, T5, R> Function3<T3, T4, T5, R> curry(T1 t1, T2 t2,
			Function5<T1, T2, T3, T4, T5, R> function) {
		return (t3, t4, t5) -> function.apply(t1, t2, t3, t4, t5);
	}

	public static <T1, T2, T3, T4, T5, R> BiFunction<T4, T5, R> curry(T1 t1, T2 t2, T3 t3,
			Function5<T1, T2, T3, T4, T5, R> function) {
		return (t4, t5) -> function.apply(t1, t2, t3, t4, t5);
	}

	public static <T1, T2, T3, T4, T5, R> Function<T5, R> curry(T1 t1, T2 t2, T3 t3, T4 t4,
			Function5<T1, T2, T3, T4, T5, R> function) {
		return (t5) -> function.apply(t1, t2, t3, t4, t5);
	}

	public static <T1, T2, R> Function<T1, R> rCurry(T2 t2, BiFunction<T1, T2, R> function) {
		return t1 -> function.apply(t1, t2);
	}

	public static <T1, T2, T3, R> BiFunction<T1, T2, R> rCurry(T3 t3, Function3<T1, T2, T3, R> function) {
		return (t1, t2) -> function.apply(t1, t2, t3);
	}

	public static <T1, T2, T3, R> Function<T1, R> rCurry(T2 t2, T3 t3, Function3<T1, T2, T3, R> function) {
		return t1 -> function.apply(t1, t2, t3);
	}

	public static <T1, T2, T3, T4, R> Function3<T1, T2, T3, R> rCurry(T4 t4, Function4<T1, T2, T3, T4, R> function) {
		return (t1, t2, t3) -> function.apply(t1, t2, t3, t4);
	}

	public static <T1, T2, T3, T4, R> BiFunction<T1, T2, R> rCurry(T3 t3, T4 t4, Function4<T1, T2, T3, T4, R> function) {
		return (t1, t2) -> function.apply(t1, t2, t3, t4);
	}

	public static <T1, T2, T3, T4, R> Function<T1, R> rCurry(T2 t2, T3 t3, T4 t4, Function4<T1, T2, T3, T4, R> function) {
		return (t1) -> function.apply(t1, t2, t3, t4);
	}

	public static <T1, T2, T3, T4, T5, R> Function4<T1, T2, T3, T4, R> rCurry(T5 t5,
			Function5<T1, T2, T3, T4, T5, R> function) {
		return (t1, t2, t3, t4) -> function.apply(t1, t2, t3, t4, t5);
	}

	public static <T1, T2, T3, T4, T5, R> Function3<T1, T2, T3, R> rCurry(T4 t4, T5 t5,
			Function5<T1, T2, T3, T4, T5, R> function) {
		return (t1, t2, t3) -> function.apply(t1, t2, t3, t4, t5);
	}

	public static <T1, T2, T3, T4, T5, R> BiFunction<T1, T2, R> rCurry(T3 t3, T4 t4, T5 t5,
			Function5<T1, T2, T3, T4, T5, R> function) {
		return (t1, t2) -> function.apply(t1, t2, t3, t4, t5);
	}

	public static <T1, T2, T3, T4, T5, R> Function<T1, R> rCurry(T2 t2, T3 t3, T4 t4, T5 t5,
			Function5<T1, T2, T3, T4, T5, R> function) {
		return (t1) -> function.apply(t1, t2, t3, t4, t5);
	}
	
	
	public static <T1, T2> Consumer<T2> curry(T1 t1, BiConsumer<T1, T2> consumer) {
		return t2 -> consumer.accept(t1, t2);
	}

	public static <T1, T2, T3> BiConsumer<T2, T3> curry(T1 t1, Consumer3<T1, T2, T3> consumer) {
		return (t2, t3) -> consumer.accept(t1, t2, t3);
	}

	public static <T1, T2, T3> Consumer<T3> curry(T1 t1, T2 t2, Consumer3<T1, T2, T3> consumer) {
		return (t3) -> consumer.accept(t1, t2, t3);
	}

	public static <T1, T2, T3, T4> Consumer3<T2, T3, T4> curry(T1 t1, Consumer4<T1, T2, T3, T4> consumer) {
		return (t2, t3, t4) -> consumer.accept(t1, t2, t3, t4);
	}

	public static <T1, T2, T3, T4> BiConsumer<T3, T4> curry(T1 t1, T2 t2, Consumer4<T1, T2, T3, T4> consumer) {
		return (t3, t4) -> consumer.accept(t1, t2, t3, t4);
	}

	public static <T1, T2, T3, T4> Consumer<T4> curry(T1 t1, T2 t2, T3 t3, Consumer4<T1, T2, T3, T4> consumer) {
		return (t4) -> consumer.accept(t1, t2, t3, t4);
	}

	public static <T1, T2, T3, T4, T5> Consumer4<T2, T3, T4, T5> curry(T1 t1,
			Consumer5<T1, T2, T3, T4, T5> consumer) {
		return (t2, t3, t4, t5) -> consumer.accept(t1, t2, t3, t4, t5);
	}

	public static <T1, T2, T3, T4, T5> Consumer3<T3, T4, T5> curry(T1 t1, T2 t2,
			Consumer5<T1, T2, T3, T4, T5> consumer) {
		return (t3, t4, t5) -> consumer.accept(t1, t2, t3, t4, t5);
	}

	public static <T1, T2, T3, T4, T5> BiConsumer<T4, T5> curry(T1 t1, T2 t2, T3 t3,
			Consumer5<T1, T2, T3, T4, T5> consumer) {
		return (t4, t5) -> consumer.accept(t1, t2, t3, t4, t5);
	}

	public static <T1, T2, T3, T4, T5> Consumer<T5> curry(T1 t1, T2 t2, T3 t3, T4 t4,
			Consumer5<T1, T2, T3, T4, T5> consumer) {
		return (t5) -> consumer.accept(t1, t2, t3, t4, t5);
	}

	public static <T1, T2> Consumer<T1> rCurry(T2 t2, BiConsumer<T1, T2> consumer) {
		return t1 -> consumer.accept(t1, t2);
	}

	public static <T1, T2, T3> BiConsumer<T1, T2> rCurry(T3 t3, Consumer3<T1, T2, T3> consumer) {
		return (t1, t2) -> consumer.accept(t1, t2, t3);
	}

	public static <T1, T2, T3> Consumer<T1> rCurry(T2 t2, T3 t3, Consumer3<T1, T2, T3> consumer) {
		return t1 -> consumer.accept(t1, t2, t3);
	}

	public static <T1, T2, T3, T4> Consumer3<T1, T2, T3> rCurry(T4 t4, Consumer4<T1, T2, T3, T4> consumer) {
		return (t1, t2, t3) -> consumer.accept(t1, t2, t3, t4);
	}

	public static <T1, T2, T3, T4> BiConsumer<T1, T2> rCurry(T3 t3, T4 t4, Consumer4<T1, T2, T3, T4> consumer) {
		return (t1, t2) -> consumer.accept(t1, t2, t3, t4);
	}

	public static <T1, T2, T3, T4> Consumer<T1> rCurry(T2 t2, T3 t3, T4 t4, Consumer4<T1, T2, T3, T4> consumer) {
		return (t1) -> consumer.accept(t1, t2, t3, t4);
	}

	public static <T1, T2, T3, T4, T5> Consumer4<T1, T2, T3, T4> rCurry(T5 t5,
			Consumer5<T1, T2, T3, T4, T5> consumer) {
		return (t1, t2, t3, t4) -> consumer.accept(t1, t2, t3, t4, t5);
	}

	public static <T1, T2, T3, T4, T5> Consumer3<T1, T2, T3> rCurry(T4 t4, T5 t5,
			Consumer5<T1, T2, T3, T4, T5> consumer) {
		return (t1, t2, t3) -> consumer.accept(t1, t2, t3, t4, t5);
	}

	public static <T1, T2, T3, T4, T5> BiConsumer<T1, T2> rCurry(T3 t3, T4 t4, T5 t5,
			Consumer5<T1, T2, T3, T4, T5> consumer) {
		return (t1, t2) -> consumer.accept(t1, t2, t3, t4, t5);
	}

	public static <T1, T2, T3, T4, T5> Consumer<T1> rCurry(T2 t2, T3 t3, T4 t4, T5 t5,
			Consumer5<T1, T2, T3, T4, T5> consumer) {
		return (t1) -> consumer.accept(t1, t2, t3, t4, t5);
	}
}
