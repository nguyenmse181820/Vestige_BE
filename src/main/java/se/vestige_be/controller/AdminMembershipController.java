package se.vestige_be.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import se.vestige_be.dto.request.MembershipPlanRequest;
import se.vestige_be.dto.response.ApiResponse;
import se.vestige_be.exception.ResourceNotFoundException;
import se.vestige_be.pojo.FeeTier;
import se.vestige_be.pojo.MembershipPlan;
import se.vestige_be.repository.FeeTierRepository;
import se.vestige_be.repository.MembershipPlanRepository;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/membership-plans")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminMembershipController {

    private final MembershipPlanRepository membershipPlanRepository;
    private final FeeTierRepository feeTierRepository;

    @GetMapping
    public ResponseEntity<ApiResponse> getAllPlans() {
        List<MembershipPlan> plans = membershipPlanRepository.findAll();
        return ResponseEntity.ok(ApiResponse.success("Membership plans retrieved successfully", plans));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse> getPlanById(@PathVariable Long id) {
        MembershipPlan plan = membershipPlanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Membership plan not found with id: " + id));
        return ResponseEntity.ok(ApiResponse.success("Membership plan retrieved successfully", plan));
    }

    @PostMapping
    public ResponseEntity<ApiResponse> createPlan(@Valid @RequestBody MembershipPlanRequest request) {
        FeeTier feeTier = feeTierRepository.findById(request.getFeeTierId())
                .orElseThrow(() -> new ResourceNotFoundException("FeeTier not found with id: " + request.getFeeTierId()));

        MembershipPlan newPlan = MembershipPlan.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .boostsPerMonth(request.getBoostsPerMonth())
                .stripePriceId(request.getStripePriceId())
                .requiredTrustTier(request.getRequiredTrustTier())
                .feeTier(feeTier)
                .isActive(request.isActive())
                .build();

        MembershipPlan savedPlan = membershipPlanRepository.save(newPlan);
        log.info("Created new membership plan: {}", savedPlan.getName());
        return ResponseEntity.ok(ApiResponse.success("Membership plan created successfully", savedPlan));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse> updatePlan(@PathVariable Long id, @Valid @RequestBody MembershipPlanRequest request) {
        MembershipPlan existingPlan = membershipPlanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Membership plan not found with id: " + id));

        FeeTier feeTier = feeTierRepository.findById(request.getFeeTierId())
                .orElseThrow(() -> new ResourceNotFoundException("FeeTier not found with id: " + request.getFeeTierId()));

        existingPlan.setName(request.getName());
        existingPlan.setDescription(request.getDescription());
        existingPlan.setPrice(request.getPrice());
        existingPlan.setBoostsPerMonth(request.getBoostsPerMonth());
        existingPlan.setStripePriceId(request.getStripePriceId());
        existingPlan.setRequiredTrustTier(request.getRequiredTrustTier());
        existingPlan.setFeeTier(feeTier);
        existingPlan.setActive(request.isActive());

        MembershipPlan updatedPlan = membershipPlanRepository.save(existingPlan);
        log.info("Updated membership plan: {}", updatedPlan.getName());
        return ResponseEntity.ok(ApiResponse.success("Membership plan updated successfully", updatedPlan));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deletePlan(@PathVariable Long id) {
        if (!membershipPlanRepository.existsById(id)) {
            throw new ResourceNotFoundException("Membership plan not found with id: " + id);
        }
        membershipPlanRepository.deleteById(id);
        log.info("Deleted membership plan with id: {}", id);
        return ResponseEntity.ok(ApiResponse.success("Membership plan deleted successfully", null));
    }

    @PatchMapping("/{id}/toggle-status")
    public ResponseEntity<ApiResponse> togglePlanStatus(@PathVariable Long id) {
        MembershipPlan plan = membershipPlanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Membership plan not found with id: " + id));

        plan.setActive(!plan.isActive());
        MembershipPlan updatedPlan = membershipPlanRepository.save(plan);

        log.info("Toggled membership plan {} status to: {}", plan.getName(), plan.isActive());
        return ResponseEntity.ok(ApiResponse.success("Membership plan status updated successfully", updatedPlan));
    }
}