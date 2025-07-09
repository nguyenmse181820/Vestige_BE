package se.vestige_be.pojo.enums;

public enum OrderItemStatus {
    // Item is part of an order, but payment is not yet complete.
    PENDING,

    // Payment is confirmed. Seller is preparing the item for pickup.
    PROCESSING,

    // Seller has requested pickup. Item is ready for the Vestige Shipping team.
    AWAITING_PICKUP,

    // Vestige Shipping has collected the item and it's at the warehouse.
    IN_WAREHOUSE,

    // Vestige Shipping is on the way to the buyer's address.
    OUT_FOR_DELIVERY,

    // The buyer has successfully received the item.
    DELIVERED,

    // The item has been cancelled.
    CANCELLED,

    // The item has been refunded.
    REFUNDED
}