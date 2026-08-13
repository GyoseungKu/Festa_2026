package org.syu_likelion.Festa_2026.lostitem;

public enum LostItemStatus {
    HOLDING("보관 중"),
    RETURNED("주인에게 돌아감");

    private final String label;

    LostItemStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
