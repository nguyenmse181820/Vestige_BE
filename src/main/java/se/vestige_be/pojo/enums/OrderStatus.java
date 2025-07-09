package se.vestige_be.pojo.enums;

public enum OrderStatus {
    PENDING,
    PAID,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    REFUNDED,
    // NOTE: EXPIRED is not currently supported by the database constraint
    // Use CANCELLED for expired orders instead
    EXPIRED
}