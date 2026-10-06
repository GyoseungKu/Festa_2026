package org.syu_likelion.Festa_2026.stamp;

import java.util.List;

final class StampBoardPolicy {
    static final int MAX_STAMPS = 6;

    private StampBoardPolicy() { }

    static boolean complete(List<BoothStamp> board) {
        // Preserve existing boards collected before the six-stamp limit.
        return board.size() >= MAX_STAMPS;
    }
}
