package se.vestige_be.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import se.vestige_be.service.TrustScoreService;
import se.vestige_be.service.UserService;
import se.vestige_be.pojo.User;

@RestController
@RequestMapping("/api/admin/trust-score")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class TrustScoreController {

    private final TrustScoreService trustScoreService;
    private final UserService userService;

    /**
     * Manually trigger trust score update for all users
     */
    @PostMapping("/update-all")
    public ResponseEntity<String> updateAllTrustScores() {
        trustScoreService.updateAllUserTrustScores();
        return ResponseEntity.ok("Trust scores updated for all users");
    }

    /**
     * Update trust score for a specific user
     */
    @PostMapping("/update/{userId}")
    public ResponseEntity<String> updateUserTrustScore(@PathVariable Long userId) {
        User user = userService.findById(userId);
        trustScoreService.updateUserTrustScore(user);
        return ResponseEntity.ok("Trust score updated for user ID: " + userId);
    }

    /**
     * Preview trust score calculation for a user without saving
     */
    @GetMapping("/preview/{userId}")
    public ResponseEntity<Integer> previewTrustScore(@PathVariable Long userId) {
        User user = userService.findById(userId);
        int previewScore = trustScoreService.calculateTrustScorePreview(user);
        return ResponseEntity.ok(previewScore);
    }
}
