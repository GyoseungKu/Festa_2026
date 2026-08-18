package org.syu_likelion.Festa_2026.poll;

public enum PollQuestionType {
    SINGLE_CHOICE, MULTIPLE_CHOICE, SHORT_TEXT, LONG_TEXT;

    public boolean isChoice() {
        return this == SINGLE_CHOICE || this == MULTIPLE_CHOICE;
    }
}
