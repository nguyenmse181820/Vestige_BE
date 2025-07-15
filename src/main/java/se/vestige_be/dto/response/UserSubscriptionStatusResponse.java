package se.vestige_be.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import se.vestige_be.dto.UserMembershipDTO;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserSubscriptionStatusResponse {
    private UserMembershipDTO activeMembership;
    private List<UserMembershipDTO> queuedMemberships;
    private LocalDateTime finalExpirationDate;
    private Integer totalBoostsAvailable;
}