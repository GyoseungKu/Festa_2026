package org.syu_likelion.Festa_2026.booth;

public enum BoothCategory {
    PHOTO_BOOTH("포토부스"),
    GENERAL("일반부스(동아리 등)"),
    FOOD_TRUCK("푸드트럭"),
    STUDENT_COUNCIL("학생회·팔찌배부존"),
    CAMPUS("교내 기관·부서 운영 부스"),
    EXTERNAL("외부 부스"),
    OTHER("기타");

    private final String label;

    BoothCategory(String label) { this.label = label; }

    public String getLabel() { return label; }

    public int getDisplayOrder() {
        return switch (this) {
            case GENERAL -> 0;
            case EXTERNAL -> 1;
            case STUDENT_COUNCIL -> 2;
            case CAMPUS -> 3;
            case PHOTO_BOOTH -> 4;
            case FOOD_TRUCK -> 5;
            case OTHER -> 6;
        };
    }
}
