package se.vestige_be.mapper;

import org.springframework.stereotype.Component;
import se.vestige_be.dto.*;
import se.vestige_be.pojo.MembershipPlan;
import se.vestige_be.pojo.Role;
import se.vestige_be.pojo.User;
import se.vestige_be.pojo.UserMembership;

@Component
public class ModelMapper {

    public RoleDTO toRoleDTO(Role role) {
        if (role == null) return null;
        RoleDTO dto = new RoleDTO();
        dto.setName(role.getName());
        return dto;
    }

    public UserDTO toUserDTO(User user) {
        if (user == null) return null;
        UserDTO dto = new UserDTO();
        dto.setId(user.getUserId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setRole(toRoleDTO(user.getRole()));
        return dto;
    }

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
        dto.setUser(toUserDTO(membership.getUser()));
        dto.setPlan(toMembershipPlanDTO(membership.getPlan()));
        dto.setStatus(membership.getStatus());
        dto.setStartDate(membership.getStartDate());
        dto.setEndDate(membership.getEndDate());
        dto.setPayosSubscriptionId(membership.getPayosSubscriptionId());
        return dto;
    }
}