package se.vestige_be.mapper;

import org.springframework.stereotype.Component;
import se.vestige_be.dto.*;
import se.vestige_be.pojo.MembershipPlan;
import se.vestige_be.pojo.User;
import se.vestige_be.pojo.UserMembership;

@Component
public class ModelMapper {

    public MembershipPlanDTO toMembershipPlanDTO(MembershipPlan plan) {
        if (plan == null) return null;
        MembershipPlanDTO dto = new MembershipPlanDTO();
        dto.setId(plan.getPlanId());
        dto.setName(plan.getName());
        dto.setDescription(plan.getDescription());
        return dto;
    }

    public UserMembershipDTO toUserMembershipDTO(UserMembership membership) {
        if (membership == null) return null;
        UserMembershipDTO dto = new UserMembershipDTO();
        dto.setId(membership.getMembershipId());

        User user = membership.getUser();
        if (user != null) {
            dto.setUserId(user.getUserId());
            dto.setUsername(user.getUsername());
            dto.setEmail(user.getEmail());
            dto.setRoleName(user.getRole() != null ? user.getRole().getName() : null);
        }
        
        dto.setPlan(toMembershipPlanDTO(membership.getPlan()));
        dto.setStatus(membership.getStatus());
        dto.setStartDate(membership.getStartDate());
        dto.setEndDate(membership.getEndDate());
        dto.setPayosSubscriptionId(membership.getPayosSubscriptionId());
        return dto;
    }
}