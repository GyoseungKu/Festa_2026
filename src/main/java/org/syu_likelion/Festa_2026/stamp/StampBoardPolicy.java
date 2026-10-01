package org.syu_likelion.Festa_2026.stamp;

import java.util.List;
import org.syu_likelion.Festa_2026.booth.BoothCategory;

final class StampBoardPolicy {
    static final int MAX_STAMPS = 6;

    private StampBoardPolicy() { }

    static boolean hasExternal(List<BoothStamp> board) {
        return board.stream().anyMatch(stamp -> stamp.getBooth().getCategory() == BoothCategory.EXTERNAL);
    }

    static boolean complete(List<BoothStamp> board) {
        // Preserve existing boards collected before the six-stamp limit.
        return board.size() >= MAX_STAMPS && hasExternal(board);
    }
}
