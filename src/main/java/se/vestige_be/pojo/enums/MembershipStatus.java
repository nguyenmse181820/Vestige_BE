package se.vestige_be.pojo.enums;

public enum MembershipStatus {
    PENDING,       // Awaiting payment for a new subscription
    ACTIVE,        // Currently in use
    CANCELLED,     // Canceled by user, will not renew
    EXPIRED,       // The service period has ended
    PENDING_EXTEND,// Awaiting payment for an extension
    QUEUED         // Paid for, waiting for its start_date to begin
}