package org.syu_likelion.Festa_2026.admin;

import java.util.List;
import java.util.stream.IntStream;

final class AdminPagination {
    private AdminPagination() { }
    static List<Integer> window(int current, int totalPages) {
        if (totalPages <= 0) return List.of();
        int start = Math.max(0, current - 3);
        int end = Math.min(totalPages - 1, start + 6);
        start = Math.max(0, end - 6);
        return IntStream.rangeClosed(start, end).boxed().toList();
    }
}
