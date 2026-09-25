package org.syu_likelion.Festa_2026.birthday;

public enum BirthdayMessageSort {
    RANDOM("랜덤순"),
    LATEST("최신순"),
    OLDEST("오래된순"),
    MOST_LIKED("하트순");

    private final String label;

    BirthdayMessageSort(String label) { this.label = label; }
    public String getLabel() { return label; }
}
