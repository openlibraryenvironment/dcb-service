package org.olf.dcb.utils;

import java.util.*;
import java.util.regex.*;

public class EditionNormalizer {

    private static final Map<String, Integer> wordToNumberMap = Map.ofEntries(
        Map.entry("first", 1),
        Map.entry("second", 2),
        Map.entry("third", 3),
        Map.entry("fourth", 4),
        Map.entry("fifth", 5),
        Map.entry("sixth", 6),
        Map.entry("seventh", 7),
        Map.entry("eighth", 8),
        Map.entry("ninth", 9),
        Map.entry("tenth", 10)
    );

    private static final List<String> knownQualifiers = List.of(
        "abridged", "revised", "illustrated", "expanded", "updated"
    );

    public static String normalizeEdition(String editionInput) {
        if (editionInput == null || editionInput.trim().isEmpty()) {
            return editionInput;
        }

        String cleaned = editionInput
                .toLowerCase()
                .replaceAll("[^a-z0-9\\s]", " ") // replace punctuation with space
                .replaceAll("\\s+", " ")         // collapse multiple spaces
                .trim();

        // Detect modifiers
        List<String> detectedModifiers = new ArrayList<>();
        for (String qualifier : knownQualifiers) {
            if (cleaned.contains(qualifier)) {
                detectedModifiers.add(qualifier);
            }
        }

        // Handle already-normalized forms like "1e"
        if (cleaned.matches("\\b\\d{1,2}e\\b")) {
            return cleaned + formatModifiers(detectedModifiers);
        }

        // Handle formats like "1d", "1st", "1st ed", "1st edition", etc.
        Pattern numericPattern = Pattern.compile("\\b(\\d{1,2})(?:st|nd|rd|th|d)?\\b(?:\\s+(?:ed|ed\\.|d\\.))?", Pattern.CASE_INSENSITIVE);
        Matcher numericMatcher = numericPattern.matcher(cleaned);
        if (numericMatcher.find()) {
            return numericMatcher.group(1) + "e" + formatModifiers(detectedModifiers);
        }

        // Handle textual ordinals like "first edition"
        for (Map.Entry<String, Integer> entry : wordToNumberMap.entrySet()) {
            if (cleaned.contains(entry.getKey())) {
                return entry.getValue() + "e" + formatModifiers(detectedModifiers);
            }
        }

        // No edition detected — return original
        return editionInput;
    }

    private static String formatModifiers(List<String> mods) {
        if (mods.isEmpty()) return "";
        return " (" + String.join(", ", mods) + ")";
    }
}

