package org.syu_likelion.Festa_2026.bamboo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.syu_likelion.Festa_2026.error.ApiException;

class BambooContentTests {

    @Test
    void trimsAndCollapsesRepeatedWhitespace() {
        assertThat(BambooService.normalizeContent("  무대   미쳤다  ")).isEqualTo("무대 미쳤다");
        assertThat(BambooService.normalizeContent("첫 줄\r\n둘째 줄")).isEqualTo("첫 줄\n둘째 줄");
        assertThat(BambooService.normalizeContent("첫 줄\n\n\n\n둘째 줄")).isEqualTo("첫 줄\n\n둘째 줄");
    }

    @Test
    void rejectsBlankContent() {
        assertThatThrownBy(() -> BambooService.normalizeContent("   \n  "))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo("BAMBOO_CONTENT_REQUIRED");
    }

    @Test
    void rejectsLineBreakFlooding() {
        String flood = "밀어내기" + "\na".repeat(20);
        assertThatThrownBy(() -> BambooService.normalizeContent(flood))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo("BAMBOO_TOO_MANY_LINE_BREAKS");
    }

    @Test
    void allowsUpToTheLineBreakLimit() {
        String allowed = "1\n2\n3\n4\n5\n6";
        assertThat(BambooService.normalizeContent(allowed)).isEqualTo(allowed);
    }

    @Test
    void countsEmojiAsSingleCharacters() {
        String emoji = "🦁".repeat(BambooService.MAX_CONTENT_CODE_POINTS);
        assertThat(BambooService.normalizeContent(emoji)).isEqualTo(emoji);

        String tooLong = "🦁".repeat(BambooService.MAX_CONTENT_CODE_POINTS + 1);
        assertThatThrownBy(() -> BambooService.normalizeContent(tooLong))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo("BAMBOO_CONTENT_TOO_LONG");
    }
}
