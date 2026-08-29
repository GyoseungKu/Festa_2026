package org.syu_likelion.Festa_2026.bamboo;

import java.text.Normalizer;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 닉네임 정규화·검증·랜덤 생성.
 *
 * <p>닉네임 중복 금지가 사칭을 막는 유일한 장치이므로 단순 문자열 비교로는 부족하다.
 * 제로폭 문자, 대소문자, 공백, 호모글리프를 제거한 정규화 키로 중복과 금칙어를 판정한다.
 *
 * <p>순수 로직만 담는다. 중복 여부 조회는 {@link BambooService}가 수행한다.
 */
public final class BambooNicknamePolicy {
    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 12;

    /** 정규화 키 기준 부분 문자열로 검사한다. "운영진입니다" 같은 변형도 함께 막힌다. */
    private static final Set<String> BLOCKED = Set.of(
            "운영진", "관리자", "총학", "공지", "스탭", "스태프", "시스템", "사무국", "주최측",
            "admin", "administrator", "staff", "system", "festa", "멋사", "멋쟁이사자처럼"
    ).stream().map(BambooNicknamePolicy::normalize).collect(Collectors.toUnmodifiableSet());

    private static final List<String> ADJECTIVES = List.of(
            "졸린", "조용한", "배고픈", "신난", "느긋한", "부지런한", "수줍은", "씩씩한", "엉뚱한", "용감한",
            "웃는", "재빠른", "차분한", "통통한", "포근한", "활발한", "뾰족한", "반짝이는", "나른한", "다정한",
            "딱딱한", "말랑한", "새침한", "상냥한", "어리둥절한", "익살맞은", "진지한", "촐랑대는", "푸근한", "하품하는");

    private static final List<String> ANIMALS = List.of(
            "사자", "여우", "토끼", "고양이", "강아지", "판다", "너구리", "다람쥐", "수달", "펭귄",
            "코알라", "하마", "기린", "얼룩말", "호랑이", "곰", "늑대", "참새", "올빼미", "비둘기",
            "두더지", "고슴도치", "청설모", "오리", "거위", "물개", "바다표범", "알파카", "낙타", "미어캣");

    private BambooNicknamePolicy() { }

    /**
     * 닉네임 중복·금칙어 판정용 정규화 키를 만든다.
     *
     * <p>NFKC → 제로폭·제어·공백 문자 제거 → 소문자 → 호모글리프 매핑 순서로 처리한다.
     * 소문자 변환을 먼저 해야 대문자 {@code I}와 소문자 {@code l}이 같은 값으로 모인다.
     * 닉네임에는 애초에 공백을 허용하지 않으므로 공백 제거가 부작용을 만들지 않는다.
     */
    public static String normalize(String raw) {
        return fold(raw, true);
    }

    /**
     * 본문 대조용 정규화. 대소문자·전각·제로폭·호모글리프만 접고 <b>공백은 남긴다</b>.
     *
     * <p>공백까지 지우면 한국어에서 오탐이 심하다. 예를 들어 "다시 발표"가 "다시발표"가 되어
     * 짧은 금칙어에 걸린다. 공백 사이를 벌리는 회피는 막지 못하지만, 정상 문장을 막는 쪽이
     * 훨씬 큰 문제라 이쪽을 택한다.
     */
    public static String normalizeText(String raw) {
        return fold(raw, false);
    }

    private static String fold(String raw, boolean stripWhitespace) {
        if (raw == null) return "";
        String nfkc = Normalizer.normalize(raw, Normalizer.Form.NFKC);
        StringBuilder key = new StringBuilder(nfkc.length());
        nfkc.codePoints().forEach(codePoint -> {
            if (isIgnorable(codePoint, stripWhitespace)) return;
            key.appendCodePoint(homoglyph(Character.toLowerCase(codePoint)));
        });
        return key.toString();
    }

    private static boolean isIgnorable(int codePoint, boolean stripWhitespace) {
        if (Character.isISOControl(codePoint)) return true;
        if (stripWhitespace && Character.isWhitespace(codePoint)) return true;
        return Character.getType(codePoint) == Character.FORMAT
                || codePoint == 0x200B || codePoint == 0x200C || codePoint == 0x200D || codePoint == 0xFEFF;
    }

    private static int homoglyph(int lowered) {
        return switch (lowered) {
            case 'l', 'i', '1', '|' -> '1';
            case 'o', '0' -> '0';
            default -> lowered;
        };
    }

    /** 표시용 원본에 허용되는 문자인지 검사한다. 공백과 이모지는 허용하지 않는다. */
    public static boolean hasOnlyAllowedCharacters(String nickname) {
        return nickname.codePoints().allMatch(codePoint ->
                (codePoint >= 0xAC00 && codePoint <= 0xD7A3)      // 한글 음절
                        || (codePoint >= 0x3131 && codePoint <= 0x318E)  // 한글 호환 자모 (ㅋㅋ 등)
                        || (codePoint >= 'a' && codePoint <= 'z')
                        || (codePoint >= 'A' && codePoint <= 'Z')
                        || (codePoint >= '0' && codePoint <= '9')
                        || codePoint == '_' || codePoint == '-');
    }

    public static int length(String nickname) {
        return nickname.codePointCount(0, nickname.length());
    }

    public static boolean isBlocked(String nicknameKey) {
        return BLOCKED.stream().anyMatch(nicknameKey::contains);
    }

    /**
     * 랜덤 닉네임 후보. 형용사 30 × 동물 30 × 숫자 100 = 90,000 조합.
     * 중복 확인과 재시도는 호출자가 수행한다.
     */
    public static String randomCandidate(int digits) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String adjective = ADJECTIVES.get(random.nextInt(ADJECTIVES.size()));
        String animal = ANIMALS.get(random.nextInt(ANIMALS.size()));
        int bound = (int) Math.pow(10, digits);
        return adjective + animal + String.format("%0" + digits + "d", random.nextInt(bound));
    }

    static List<String> adjectives() { return ADJECTIVES; }
    static List<String> animals() { return ANIMALS; }
}
