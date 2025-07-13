package se.vestige_be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import se.vestige_be.dto.request.ApproveCertificationRequest;
import se.vestige_be.dto.request.DocumentRequestDto;
import se.vestige_be.dto.request.RejectCertificationRequest;
import se.vestige_be.dto.response.ApiResponse;
import se.vestige_be.dto.response.CertificationResponse;
import se.vestige_be.dto.response.DocumentResponse;
import se.vestige_be.dto.response.PagedResponse;
import se.vestige_be.pojo.LegitProfileCertification;
import se.vestige_be.pojo.enums.CertificationStatus;
import se.vestige_be.service.CertificationService;
import se.vestige_be.service.UserService;
import se.vestige_be.util.PaginationUtils;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/certifications")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Certification Management", 
     description = "API for user profile certification submission and admin approval/rejection")
public class CertificationController {

    private final CertificationService certificationService;
    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "Submit certification request",
        description = "Submit a certification request with document URLs. Documents must be uploaded to cloud storage beforehand."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "Certification request submitted successfully"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request data or user already has pending/approved certification"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized"
        )
    })
    public ResponseEntity<ApiResponse<CertificationResponse>> submitCertification(
        @Valid @RequestBody List<DocumentRequestDto> documentRequests,
        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        
        // Get the authenticated user's ID
        Long currentUserId = userService.findByUsername(userDetails.getUsername()).getUserId();
        
        LegitProfileCertification certification = certificationService.submitCertification(currentUserId, documentRequests);
        
        CertificationResponse response = convertToCertificationResponse(certification);
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<CertificationResponse>builder()
                        .message("Certification request submitted successfully")
                        .data(response)
                        .build());
    }

    @GetMapping("/my-status")
    @PreAuthorize("hasRole('USER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "Get my certification status",
        description = "Get the current user's certification status and details"
    )
    public ResponseEntity<ApiResponse<CertificationResponse>> getMyCertificationStatus(
        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        
        Long currentUserId = userService.findByUsername(userDetails.getUsername()).getUserId();
        
        LegitProfileCertification certification = certificationService.getCertificationByUserId(currentUserId);
        
        if (certification == null) {
            return ResponseEntity.ok(ApiResponse.<CertificationResponse>builder()
                    .message("No certification request found")
                    .data(null)
                    .build());
        }
        
        CertificationResponse response = convertToCertificationResponse(certification);
        
        return ResponseEntity.ok(ApiResponse.<CertificationResponse>builder()
                .message("Certification status retrieved successfully")
                .data(response)
                .build());
    }

    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "[ADMIN] Get all certification requests",
        description = "Admin endpoint to get all certification requests with pagination"
    )
    public ResponseEntity<ApiResponse<PagedResponse<CertificationResponse>>> getAllCertifications(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt") String sortBy,
        @RequestParam(defaultValue = "desc") String sortDir,
        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails adminDetails) {
        
        Pageable pageable = PaginationUtils.createPageable(page, size, sortBy, sortDir);
        Page<LegitProfileCertification> certifications = certificationService.getAllCertifications(pageable);
        
        Page<CertificationResponse> certificationResponsePage = certifications.map(this::convertToCertificationResponse);
        
        PagedResponse<CertificationResponse> pagedResponse = PagedResponse.of(certificationResponsePage);
        
        return ResponseEntity.ok(ApiResponse.<PagedResponse<CertificationResponse>>builder()
                .message("Certifications retrieved successfully")
                .data(pagedResponse)
                .build());
    }

    @GetMapping("/admin/pending")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "[ADMIN] Get pending certification requests",
        description = "Admin endpoint to get all pending certification requests"
    )
    public ResponseEntity<ApiResponse<List<CertificationResponse>>> getPendingCertifications(
        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails adminDetails) {
        
        List<LegitProfileCertification> certifications = certificationService.getCertificationsByStatus(CertificationStatus.PENDING);
        
        List<CertificationResponse> responseList = certifications.stream()
                .map(this::convertToCertificationResponse)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.<List<CertificationResponse>>builder()
                .message("Pending certifications retrieved successfully")
                .data(responseList)
                .build());
    }

    @PostMapping("/admin/{certificationId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "[ADMIN] Approve certification request",
        description = "Admin endpoint to approve a certification request"
    )
    public ResponseEntity<ApiResponse<CertificationResponse>> approveCertification(
        @PathVariable Long certificationId,
        @Valid @RequestBody ApproveCertificationRequest request,
        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails adminDetails) {
        
        Long reviewerId = userService.findByUsername(adminDetails.getUsername()).getUserId();
        
        LegitProfileCertification certification = certificationService.approveCertification(
                certificationId, reviewerId, request.getExpirationMonths());
        
        CertificationResponse response = convertToCertificationResponse(certification);
        
        return ResponseEntity.ok(ApiResponse.<CertificationResponse>builder()
                .message("Certification approved successfully")
                .data(response)
                .build());
    }

    @PostMapping("/admin/{certificationId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "[ADMIN] Reject certification request",
        description = "Admin endpoint to reject a certification request"
    )
    public ResponseEntity<ApiResponse<CertificationResponse>> rejectCertification(
        @PathVariable Long certificationId,
        @Valid @RequestBody RejectCertificationRequest request,
        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails adminDetails) {
        
        Long reviewerId = userService.findByUsername(adminDetails.getUsername()).getUserId();
        
        LegitProfileCertification certification = certificationService.rejectCertification(
                certificationId, reviewerId, request.getReason());
        
        CertificationResponse response = convertToCertificationResponse(certification);
        
        return ResponseEntity.ok(ApiResponse.<CertificationResponse>builder()
                .message("Certification rejected successfully")
                .data(response)
                .build());
    }

    private CertificationResponse convertToCertificationResponse(LegitProfileCertification certification) {
        List<DocumentResponse> documentResponses = List.of();
        
        // Handle documents safely to avoid LazyInitializationException
        if (certification.getDocuments() != null) {
            try {
                documentResponses = certification.getDocuments().stream()
                        .map(doc -> DocumentResponse.builder()
                                .id(doc.getId())
                                .documentUrl(doc.getDocumentUrl())
                                .documentType(doc.getDocumentType())
                                .uploadedAt(doc.getUploadedAt())
                                .build())
                        .collect(Collectors.toList());
            } catch (Exception e) {
                // Log the error but continue with empty list
                log.warn("Could not load documents for certification {}: {}", certification.getCertificationId(), e.getMessage());
                documentResponses = List.of();
            }
        }

        // Safe access to user information
        Long userId = null;
        String username = null;
        try {
            if (certification.getUser() != null) {
                userId = certification.getUser().getUserId();
                username = certification.getUser().getUsername();
            }
        } catch (Exception e) {
            log.warn("Could not load user information for certification {}: {}", certification.getCertificationId(), e.getMessage());
        }

        // Safe access to reviewer information
        Long reviewerId = null;
        String reviewerUsername = null;
        try {
            if (certification.getReviewer() != null) {
                reviewerId = certification.getReviewer().getUserId();
                reviewerUsername = certification.getReviewer().getUsername();
            }
        } catch (Exception e) {
            log.warn("Could not load reviewer information for certification {}: {}", certification.getCertificationId(), e.getMessage());
        }

        return CertificationResponse.builder()
                .certificationId(certification.getCertificationId())
                .userId(userId)
                .username(username)
                .status(certification.getStatus())
                .certificationFee(certification.getCertificationFee())
                .approvedAt(certification.getApprovedAt())
                .expiresAt(certification.getExpiresAt())
                .reviewerId(reviewerId)
                .reviewerUsername(reviewerUsername)
                .verificationLevel(certification.getVerificationLevel())
                .createdAt(certification.getCreatedAt())
                .updatedAt(certification.getUpdatedAt())
                .documents(documentResponses)
                .build();
    }
}
