package se.vestige_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagedResponse<T> {
    private List<T> content;
    private PageMetadata pagination;
    private Map<String, Object> filters;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PageMetadata {
        private int currentPage;
        private int pageSize;
        private int totalPages;
        private Long totalElements;
    }

    public static <T> PagedResponse<T> of(Page<T> page) {
        return PagedResponse.<T>builder()
                .content(page.getContent())
                .pagination(PageMetadata.builder()
                        .currentPage(page.getNumber())
                        .pageSize(page.getSize())
                        .totalPages(page.getTotalPages())
                        .totalElements(page.getTotalElements())
                        .build())
                .build();
    }

    public static <T> PagedResponse<T> of(Page<T> page, Map<String, Object> filters) {
        PagedResponse<T> response = of(page);
        response.setFilters(filters);
        return response;
    }

}
