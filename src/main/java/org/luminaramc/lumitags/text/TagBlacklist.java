package org.luminaramc.lumitags.text;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Compares visible custom text, ignoring formatting inserted inside blocked terms. */
public final class TagBlacklist {
    private static final Pattern TAGS = Pattern.compile("<[^>]*>");
    private static final Pattern COLORS = Pattern.compile(
            "(?i)[&§]#[0-9a-f]{6}|[&§][0-9a-fk-orx]");
    private static final Pattern INVISIBLE = Pattern.compile("\\p{Cf}");

    private TagBlacklist() {}

    public static boolean isBlocked(String input, List<String> words) {
        String text = normalize(input);
        return words.stream().map(TagBlacklist::normalize)
                .anyMatch(word -> !word.isBlank() && text.contains(word));
    }

    private static String normalize(String input) {
        String text = Normalizer.normalize(input, Normalizer.Form.NFKC);
        text = INVISIBLE.matcher(text).replaceAll("");
        text = TAGS.matcher(text).replaceAll("");
        return COLORS.matcher(text).replaceAll("").toLowerCase(Locale.ROOT);
    }
}
