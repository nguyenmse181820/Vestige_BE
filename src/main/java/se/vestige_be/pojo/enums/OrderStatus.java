package se.vestige_be.pojo.enums;

public enum OrderStatus {
    // Order is created, awaiting payment.
    PENDING,

    // Payment has been confirmed. Items are being prepared and handled by our logistics team.
    // This status covers OrderItem statuses: PROCESSING, AWAITING_PICKUP, IN_WAREHOUSE.
    PROCESSING,

    // The package(s) are on their way to the buyer.
    // This status corresponds to the OrderItem status: OUT_FOR_DELIVERY.
    OUT_FOR_DELIVERY,

    // All items in the order have been successfully delivered.
    DELIVERED,

    // The entire order has been cancelled.
    CANCELLED,

    // The order has been refunded.
    REFUNDED,

    // Payment was not completed in time.
    EXPIRED
}