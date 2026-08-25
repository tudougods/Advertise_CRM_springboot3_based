package com.internship.crm.payment.entity;

/** Processing result of a payment provider callback event. */
public enum PaymentCallbackStatus {
    RECEIVED,
    PROCESSED,
    DUPLICATE,
    REJECTED
}
