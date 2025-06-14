package se.vestige_be.pojo.enums;

public enum EscrowStatus {
    HOLDING,     // Tiền đang được giữ
    RELEASED,    // Đã xác nhận delivery, chờ 7 ngày
    TRANSFERRED, // Đã chuyển tiền cho seller
    REFUNDED,     // Đã hoàn tiền
    TRANSFER_FAILED
}