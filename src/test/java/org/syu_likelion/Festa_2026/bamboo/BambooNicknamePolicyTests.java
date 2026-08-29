package org.syu_likelion.Festa_2026.bamboo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BambooNicknamePolicyTests {

    @Test
    void normalizeCollapsesCaseWhitespaceAndZeroWidthCharacters() {
        String plain = BambooNicknamePolicy.normalize("졸린사자");
        assertThat(BambooNicknamePolicy.normalize("졸린사자 ")).isEqualTo(plain);
        assertThat(BambooNicknamePolicy.normalize("졸린 사자")).isEqualTo(plain);
        assertThat(BambooNicknamePolicy.normalize("졸린​사자")).isEqualTo(plain);
        assertThat(BambooNicknamePolicy.normalize("졸린﻿사자")).isEqualTo(plain);
    }

    @Test
    void normalizeCollapsesHomoglyphs() {
        String admin = BambooNicknamePolicy.normalize("admin");
        assertThat(BambooNicknamePolicy.normalize("ADMIN")).isEqualTo(admin);
        assertThat(BambooNicknamePolicy.normalize("Adm1n")).isEqualTo(admin);
        assertThat(BambooNicknamePolicy.normalize("adm!n")).isNotEqualTo(admin);
        assertThat(BambooNicknamePolicy.normalize("l1lI")).isEqualTo("1111");
        assertThat(BambooNicknamePolicy.normalize("O0o")).isEqualTo("000");
    }

    @Test
    void normalizeAppliesNfkcSoWidthVariantsCollide() {
        assertThat(BambooNicknamePolicy.normalize("ｓｔａｆｆ"))
                .isEqualTo(BambooNicknamePolicy.normalize("staff"));
    }

    @Test
    void blocksImpersonationOfOperators() {
        assertThat(BambooNicknamePolicy.isBlocked(BambooNicknamePolicy.normalize("운영진"))).isTrue();
        assertThat(BambooNicknamePolicy.isBlocked(BambooNicknamePolicy.normalize("운영진입니다"))).isTrue();
        assertThat(BambooNicknamePolicy.isBlocked(BambooNicknamePolicy.normalize("총학생회"))).isTrue();
        assertThat(BambooNicknamePolicy.isBlocked(BambooNicknamePolicy.normalize("Adm1n"))).isTrue();
        assertThat(BambooNicknamePolicy.isBlocked(BambooNicknamePolicy.normalize("ＡＤＭＩＮ"))).isTrue();
        assertThat(BambooNicknamePolicy.isBlocked(BambooNicknamePolicy.normalize("졸린사자42"))).isFalse();
    }

    @Test
    void allowsHangulLatinDigitsAndTwoPunctuationMarksOnly() {
        assertThat(BambooNicknamePolicy.hasOnlyAllowedCharacters("졸린사자42")).isTrue();
        assertThat(BambooNicknamePolicy.hasOnlyAllowedCharacters("ㅋㅋ루삥뽕")).isTrue();
        assertThat(BambooNicknamePolicy.hasOnlyAllowedCharacters("bamboo_1-2")).isTrue();
        assertThat(BambooNicknamePolicy.hasOnlyAllowedCharacters("졸린 사자")).isFalse();
        assertThat(BambooNicknamePolicy.hasOnlyAllowedCharacters("사자🦁")).isFalse();
        assertThat(BambooNicknamePolicy.hasOnlyAllowedCharacters("사자!")).isFalse();
    }

    @Test
    void everyGeneratedCandidateSatisfiesItsOwnRules() {
        for (int digits = 2; digits <= 3; digits++) {
            for (int trial = 0; trial < 2_000; trial++) {
                String candidate = BambooNicknamePolicy.randomCandidate(digits);
                assertThat(BambooNicknamePolicy.length(candidate))
                        .as("후보 길이: %s", candidate)
                        .isBetween(BambooNicknamePolicy.MIN_LENGTH, BambooNicknamePolicy.MAX_LENGTH);
                assertThat(BambooNicknamePolicy.hasOnlyAllowedCharacters(candidate))
                        .as("후보 문자: %s", candidate).isTrue();
                assertThat(BambooNicknamePolicy.isBlocked(BambooNicknamePolicy.normalize(candidate)))
                        .as("후보가 금칙어에 걸림: %s", candidate).isFalse();
            }
        }
    }

    @Test
    void textNormalizationKeepsWhitespaceSoOrdinaryKoreanIsNotMangled() {
        assertThat(BambooNicknamePolicy.normalizeText("다시 발표할게")).isEqualTo("다시 발표할게");
        assertThat(BambooNicknamePolicy.normalizeText("다시 발표할게")).doesNotContain("시발");
        assertThat(BambooNicknamePolicy.normalize("다시 발표할게")).contains("시발");
    }

    @Test
    void textNormalizationStillFoldsCaseWidthAndZeroWidth() {
        assertThat(BambooNicknamePolicy.normalizeText("ＢＡＮＮＥＤ"))
                .isEqualTo(BambooNicknamePolicy.normalizeText("banned"));
        assertThat(BambooNicknamePolicy.normalizeText("ban​ned"))
                .isEqualTo(BambooNicknamePolicy.normalizeText("banned"));
    }

    @Test
    void wordListsAreTheDocumentedSize() {
        assertThat(BambooNicknamePolicy.adjectives()).hasSize(30).doesNotHaveDuplicates();
        assertThat(BambooNicknamePolicy.animals()).hasSize(30).doesNotHaveDuplicates();
    }
}
