package org.syu_likelion.Festa_2026.lostitem;

public enum LostItemSort {
    NEWEST("최신순"),
    OLDEST("오래된순");

    private final String label;

    LostItemSort(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
