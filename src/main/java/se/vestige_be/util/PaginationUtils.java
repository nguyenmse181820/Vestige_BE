package se.vestige_be.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.HashMap;
import java.util.Map;

public class PaginationUtils {

    public static Pageable createPageable(int page, int size, String sortBy, String sortDir) {
        Sort.Direction direction = sortDir.equalsIgnoreCase("desc") ?
                Sort.Direction.DESC : Sort.Direction.ASC;
        // Support aliases for viewCount and likeCount
        String actualSortBy;
        if ("viewCount".equalsIgnoreCase(sortBy)) {
            actualSortBy = "viewsCount";
        } else if ("likeCount".equalsIgnoreCase(sortBy)) {
            actualSortBy = "likesCount";
        } else {
            actualSortBy = sortBy;
        }
        return PageRequest.of(page, size, Sort.by(direction, actualSortBy));
    }

    public static Map<String, Object> createFilters(String search, Long categoryId, Long brandId) {
        Map<String, Object> filters = new HashMap<>();
        if (search != null) filters.put("search", search);
        if (categoryId != null) filters.put("categoryId", categoryId);
        if (brandId != null) filters.put("brandId", brandId);
        return filters;
    }

}
