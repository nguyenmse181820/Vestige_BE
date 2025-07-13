package se.vestige_be.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import se.vestige_be.dto.request.RatingRequest;
import se.vestige_be.dto.response.RatingResponse;
import se.vestige_be.dto.response.SystemRatingStatsResponse;
import se.vestige_be.service.SystemRatingService;
import se.vestige_be.service.UserService;

/**
 * Controller for system/project ratings.
 * This allows users to rate the overall system/project, not specific users.
 * Each user can submit one rating for the entire system.
 */
@RestController
@RequestMapping("/api/ratings")
@RequiredArgsConstructor
public class SystemRatingController {

    private final SystemRatingService systemRatingService;
    private final UserService userService; // To resolve username to user ID

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<RatingResponse> submitSystemRating(@Valid @RequestBody RatingRequest request) {
        Long currentUserId = getCurrentUserId();
        RatingResponse response = systemRatingService.createOrUpdateSystemRating(currentUserId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-rating")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<RatingResponse> getMySystemRating() {
        Long currentUserId = getCurrentUserId();
        return systemRatingService.getUserSystemRating(currentUserId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/stats")
    public ResponseEntity<SystemRatingStatsResponse> getSystemRatingStatistics() {
        SystemRatingStatsResponse stats = systemRatingService.getSystemRatingStatistics();
        return ResponseEntity.ok(stats);
    }

    // TODO: Implement Admin endpoints for GET /all-ratings

    private Long getCurrentUserId() {
        String username = ((UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUsername();
        return userService.findByUsername(username).getUserId();
    }
}
