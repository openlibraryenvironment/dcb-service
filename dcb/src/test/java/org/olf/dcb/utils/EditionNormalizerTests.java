package org.olf.dcb.utils;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static java.util.Collections.emptyList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.olf.dcb.utils.CollectionUtils.concatenate;
import org.olf.dcb.utils.EditionNormalizer;


import org.junit.jupiter.api.Test;

class EditionNormalizerTests {


  @ParameterizedTest(name = "\"{0}\" → \"{1}\"")
  @MethodSource("editionNormalizationCases")
  void testNormalizeEdition(String input, String expected) {
    assertEquals(expected, EditionNormalizer.normalizeEdition(input));
  }

  static Stream<Arguments> editionNormalizationCases() {
    return Stream.of(
      arguments("abridged first edition", "1e (abridged)"),
      arguments("first edition", "1e"),
      arguments("1e", "1e"),
      arguments("1d", "1e"),
      arguments("1st", "1e"),
      arguments("first", "1e"),
      arguments("first ed.", "1e"),
      arguments("first edition", "1e"),
      arguments("3rd ed. revised", "3e (revised)"),
      arguments("illustrated second edition", "2e (illustrated)"),
      arguments("expanded 4th edition", "4e (expanded)"),
      arguments("5th ed", "5e"),
      arguments("new revised and updated 6th ed.", "6e (revised, updated)"),
      arguments("edition 7", "7e"),
      arguments("unknown version", "unknown version"),
      arguments("", ""),
      arguments(null, null)
    );
  }
}
