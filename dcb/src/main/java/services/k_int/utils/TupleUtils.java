package services.k_int.utils;

import java.util.function.BiFunction;
import java.util.function.Function;

import reactor.function.Function3;
import reactor.function.Function4;
import reactor.function.Function5;
import reactor.function.Function6;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuple4;
import reactor.util.function.Tuple5;

public interface TupleUtils {
	
	public static <T1, T2, R> Function<T2, R> curry(T1 t1, BiFunction<T1, T2, R> function) {
    return t2 -> function.apply(t1, t2);
  }
	
	public static <T1, T2, T3, R> Function<Tuple2<T2, T3>, R> curry(T1 t1, Function3<T1, T2, T3, R> function) {
    return tuple -> function.apply(t1, tuple.getT1(), tuple.getT2());
  }
	
	public static <T1, T2, T3, T4, R> Function<Tuple3<T2, T3, T4>, R> curry(T1 t1, Function4<T1, T2, T3, T4, R> function) {
    return tuple -> function.apply(t1, tuple.getT1(), tuple.getT2(), tuple.getT3());
  }
	
	public static <T1, T2, T3, T4, R> Function<Tuple2<T3, T4>, R> curry(T1 t1, T2 t2, Function4<T1, T2, T3, T4, R> function) {
    return tuple -> function.apply(t1, t2, tuple.getT1(), tuple.getT2());
  }

	public static <T1, T2, T3, R> Function<T3, R> curry(T1 t1, T2 t2, Function3<T1, T2, T3, R> function) {
    return t3 -> function.apply(t1, t2, t3);
  }
	
	public static <T1, T2, T3, T4, T5, R> Function<Tuple4<T2, T3, T4, T5>, R> curry(T1 t1, Function5<T1, T2, T3, T4, T5, R> function) {
    return tuple -> function.apply(t1, tuple.getT1(), tuple.getT2(), tuple.getT3(), tuple.getT4());
  }
	
	public static <T1, T2, T3, T4, T5, R> Function<Tuple3<T3, T4, T5>, R> curry(T1 t1, T2 t2, Function5<T1, T2, T3, T4, T5, R> function) {
    return tuple -> function.apply(t1, t2, tuple.getT1(), tuple.getT2(), tuple.getT3());
  }
	
	public static <T1, T2, T3, T4, T5, R> Function<Tuple2<T4, T5>, R> curry(T1 t1, T2 t2, T3 t3, Function5<T1, T2, T3, T4, T5, R> function) {
    return tuple -> function.apply(t1, t2, t3, tuple.getT1(), tuple.getT2());
  }
	
	public static <T1, T2, T3, T4, T5, T6, R> Function<Tuple5<T2, T3, T4, T5, T6>, R> curry(T1 t1, Function6<T1, T2, T3, T4, T5, T6, R> function) {
    return tuple -> function.apply(t1, tuple.getT1(), tuple.getT2(), tuple.getT3(), tuple.getT4(), tuple.getT5());
  }
}
