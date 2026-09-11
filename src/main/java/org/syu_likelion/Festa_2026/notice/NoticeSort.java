package org.syu_likelion.Festa_2026.notice;

public enum NoticeSort {
    NEWEST("최신순"),
    OLDEST("오래된순");

    private final String label;

    NoticeSort(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
