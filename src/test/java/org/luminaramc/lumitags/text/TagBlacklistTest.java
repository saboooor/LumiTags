package org.luminaramc.lumitags.text;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TagBlacklistTest {
    @Test void detectsTermsAcrossFormatting() {
        for (String input : List.of("BLOCKEDWORD", "blocked&cword", "blocked§lword",
                "blocked<gradient:#ff0000:#ffffff>word</gradient>",
                "blocked&#abcdefword", "blocked§x§a§b§c§d§e§fword",
                "blocked&x&a&b&c&d&e&fword", "blocked\u200bword", "ｂｌｏｃｋｅｄｗｏｒｄ",
                "a blockedword here")) {
            assertTrue(TagBlacklist.isBlocked(input, List.of("blockedword")), input);
        }
    }

    @Test void allowsCleanTextAndIgnoresEmptyEntries() {
        assertFalse(TagBlacklist.isBlocked("friendly", List.of("blockedword", "", " ", "<red>")));
        assertFalse(TagBlacklist.isBlocked("anything", List.of()));
        assertTrue(TagBlacklist.isBlocked("blocked <red>phrase", List.of("blocked phrase")));
    }
}
