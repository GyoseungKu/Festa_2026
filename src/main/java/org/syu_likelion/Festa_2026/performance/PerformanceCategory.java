package org.syu_likelion.Festa_2026.performance;

public enum PerformanceCategory {
    CELEBRITY("연예인"),
    CLUB("동아리"),
    INDIVIDUAL("개인");

    private final String label;

    PerformanceCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
