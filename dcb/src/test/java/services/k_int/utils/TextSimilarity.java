package services.k_int.utils;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import info.debatty.java.stringsimilarity.Cosine;
import info.debatty.java.stringsimilarity.Damerau;
import info.debatty.java.stringsimilarity.Jaccard;
import info.debatty.java.stringsimilarity.JaroWinkler;
import info.debatty.java.stringsimilarity.Levenshtein;
import info.debatty.java.stringsimilarity.QGram;
import info.debatty.java.stringsimilarity.RatcliffObershelp;
import info.debatty.java.stringsimilarity.SorensenDice;
import info.debatty.java.stringsimilarity.interfaces.StringDistance;
import reactor.function.TupleUtils;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

public class TextSimilarity {

	private final String baseline = "Proin non ante cursus, lobortis neque quis, viverra urna. In tellus tellus, varius ac vehicula in, eleifend eget tellus. Nam quis sagittis augue. Fusce ornare tristique nunc, nec aliquam quam eleifend ut. Lorem ipsum dolor sit amet, consectetur adipiscing elit. Fusce laoreet arcu blandit, efficitur massa sit amet, auctor dui. Vestibulum orci leo, mattis et feugiat et, egestas a diam. Aliquam arcu turpis, molestie vitae massa et, mollis semper erat. Pellentesque vel fringilla tortor. Integer lacinia nibh eget auctor finibus.";

	private static final List<String> data = List.of("Pride and Prejudice by Jane Austen",
			"Pride and Prejudice by Jane Austen", "The Affair of the Brains by Anthony Gilmore",
			"Alchol and the Human Brain by Joseph Cook",
			"Alcohol and the Human Brain by Joseph Cook", "The Brain by Edmond Hamilton", "Brainchild by Henry Slesar",
			"Bring Back My Brain! by Dwight V. Swain", "Brain Teaser by Tom Godwin", "The Brain Sinner by Alan Edward Nourse",
			"Brain Twister by Randall Garrett and Laurence M. Janifer");

	@Test
	void predcalculateBlockingValue() {
		List<Tuple2<String, List<String>>> titleAndAuthors = data.stream().map(titleAndAuthStr -> {
			String[] titleAndAuths = titleAndAuthStr.split("\s+by\s+");

			String[] auths = titleAndAuths[1].split("\s+and\s+");

			return Tuples.of(titleAndAuths[0], List.of(auths));
		}).collect(Collectors.toUnmodifiableList());

		List.of(new Levenshtein(), new JaroWinkler(-1), new SorensenDice(4), new Jaccard(4), new QGram(10), new Cosine(4), new Damerau(), new RatcliffObershelp())
			.stream()
			.map(this::markWith)
			.map(this::applyDistance)
			.flatMap( distance -> {
				return titleAndAuthors.stream()
					.map(Tuple2::getT1)
					.map(distance);
			})
			.forEach(TupleUtils.consumer(this::print));
		;
		
//		titleAndAuthors.stream()
//			.map(Tuple2::getT1)
//			.map(markWith("Cosine Similarity"))
//			.map(this::applyCosineDistance)
//			.forEach(TupleUtils.consumer(this::print));
	}
	
	private <T> T markWith(T item) {
		System.out.println("\n\n" + item.getClass().getSimpleName() + "\n");
		return item;
	}
	
	private void print(String item, Object result) {
		System.out.println("\"" +item + "\" = " + result.toString() );
	}
	
	private Function<String, Tuple2<String, Double>> applyDistance( StringDistance distance ) {
		return ( target ) -> {
			var compareTo = baseline;
			if (compareTo.length() > target.length()) {
				compareTo = compareTo.substring(0, target.length());
			}
			
			return Tuples.of(target, distance.distance(target, compareTo));
		};
	}
}
