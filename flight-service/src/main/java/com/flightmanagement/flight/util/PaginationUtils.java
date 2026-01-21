package com.flightmanagement.flight.util;

import com.flightmanagement.flight.dto.PageResponse;

import java.util.Collections;
import java.util.List;

public final class PaginationUtils {

    private static final int MAX_PAGE_SIZE = 100;

    private PaginationUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static <T> PageResponse<T> paginate(List<T> allResults, int page, int size) {
        return paginate(allResults, page, size, MAX_PAGE_SIZE);
    }

    public static <T> PageResponse<T> paginate(List<T> allResults, int page, int size, int maxPageSize) {
        int normalizedPage = Math.max(0, page);
        int normalizedSize = Math.min(Math.max(1, size), maxPageSize);

        int totalElements = allResults.size();
        int totalPages = (int) Math.ceil((double) totalElements / normalizedSize);

        int fromIndex = normalizedPage * normalizedSize;
        int toIndex = Math.min(fromIndex + normalizedSize, totalElements);

        List<T> pageContent = (fromIndex < totalElements)
                ? allResults.subList(fromIndex, toIndex)
                : Collections.emptyList();

        return PageResponse.<T>builder()
                .content(pageContent)
                .page(normalizedPage)
                .size(normalizedSize)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .first(normalizedPage == 0)
                .last(normalizedPage >= totalPages - 1)
                .hasNext(normalizedPage < totalPages - 1)
                .hasPrevious(normalizedPage > 0)
                .build();
    }

    public static int normalizePageNumber(int page) {
        return Math.max(0, page);
    }

    public static int normalizePageSize(int size, int maxSize) {
        return Math.min(Math.max(1, size), maxSize);
    }
}
