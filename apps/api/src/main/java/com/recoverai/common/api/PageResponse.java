package com.recoverai.common.api;

import java.util.List;

/** Uniform paginated envelope: { items, page, size, total, totalPages }. */
public record PageResponse<T>(List<T> items, int page, int size, long total, int totalPages) {

  public static <T> PageResponse<T> of(List<T> items, int page, int size, long total) {
    int totalPages = size > 0 ? (int) Math.ceil((double) total / size) : 0;
    return new PageResponse<>(items, page, size, total, totalPages);
  }
}
