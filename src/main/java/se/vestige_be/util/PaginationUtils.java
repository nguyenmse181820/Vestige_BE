package se.vestige_be.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.HashMap;
import java.util.Map;

public class PaginationUtils {

    public static Pageable createPageable(int page, int size, String sortBy, String sortDir) {
        // Add bounds checking to prevent integer overflow
        int safePage = Math.max(0, Math.min(page, 10000));
        int safeSize = Math.max(1, Math.min(size, 100));
        
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
        return PageRequest.of(safePage, safeSize, Sort.by(direction, actualSortBy));
    }

    public static Map<String, Object> createFilters(String search, Long categoryId, Long brandId) {
        Map<String, Object> filters = new HashMap<>();
        if (search != null) filters.put("search", search);
        if (categoryId != null) filters.put("categoryId", categoryId);
        if (brandId != null) filters.put("brandId", brandId);
        return filters;
    }

}
