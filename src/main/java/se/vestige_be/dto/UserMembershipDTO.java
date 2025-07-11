package se.vestige_be.dto;

import lombok.Data;
import se.vestige_be.pojo.enums.MembershipStatus;
import java.time.LocalDateTime;

@Data
public class UserMembershipDTO {
    private Long id;
    private UserDTO user;
    private MembershipPlanDTO plan;
    private MembershipStatus status;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String payosSubscriptionId;
}