package se.vestige_be.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.vestige_be.dto.request.RatingRequest;
import se.vestige_be.dto.response.PagedResponse;
import se.vestige_be.dto.response.RatingResponse;
import se.vestige_be.dto.response.SystemRatingStatsResponse;
import se.vestige_be.exception.ResourceNotFoundException;
import se.vestige_be.pojo.SystemRating;
import se.vestige_be.pojo.User;
import se.vestige_be.repository.SystemRatingRepository;
import se.vestige_be.repository.UserRepository;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemRatingService {

    private final SystemRatingRepository systemRatingRepository;
    private final UserRepository userRepository;

    @Transactional
    public RatingResponse createOrUpdateSystemRating(Long userId, RatingRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User with ID " + userId + " not found."));

        SystemRating rating = systemRatingRepository.findByUserUserId(userId)
                .orElseGet(() -> SystemRating.builder().user(user).build());

        rating.setRating(request.getRating());
        rating.setComment(request.getComment());

        SystemRating savedRating = systemRatingRepository.save(rating);
        return convertToDto(savedRating);
    }

    public Optional<RatingResponse> getUserSystemRating(Long userId) {
        return systemRatingRepository.findByUserUserIdWithUser(userId).map(this::convertToDto);
    }

    public SystemRatingStatsResponse getSystemRatingStatistics() {
        return SystemRatingStatsResponse.builder()
                .averageRating(systemRatingRepository.findAverageRating())
                .totalRatings(systemRatingRepository.findTotalRatings())
                .oneStarCount(systemRatingRepository.countByRating(1))
                .twoStarCount(systemRatingRepository.countByRating(2))
                .threeStarCount(systemRatingRepository.countByRating(3))
                .fourStarCount(systemRatingRepository.countByRating(4))
                .fiveStarCount(systemRatingRepository.countByRating(5))
                .build();
    }

    /**
     * Get all system ratings with pagination
     */
    public PagedResponse<RatingResponse> getAllRatings(Pageable pageable) {
        Page<SystemRating> ratings = systemRatingRepository.findAllWithUser(pageable);
        Page<RatingResponse> ratingResponses = ratings.map(this::convertToDto);
        return PagedResponse.of(ratingResponses);
    }

    private RatingResponse convertToDto(SystemRating rating) {
        if (rating == null) {
            return null;
        }
        
        // Safely access user information with null checks
        Long userId = null;
        String username = "Unknown User";
        
        try {
            if (rating.getUser() != null) {
                userId = rating.getUser().getUserId();
                username = rating.getUser().getUsername();
            }
        } catch (Exception e) {
            // Handle lazy loading exception gracefully
            log.warn("Failed to load user information for system rating {}: {}", rating.getId(), e.getMessage());
        }
        
        return RatingResponse.builder()
                .id(rating.getId())
                .rating(rating.getRating())
                .comment(rating.getComment())
                .userId(userId)
                .username(username)
                .createdAt(rating.getCreatedAt())
                .updatedAt(rating.getUpdatedAt())
                .build();
    }
}
