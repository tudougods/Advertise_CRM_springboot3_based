package com.internship.crm.payment.service;

import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Generates opaque server-owned recharge order numbers. */
@Component
public class RechargeOrderNumberGenerator {

    public String nextOrderNo() {
        return "RCH-" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .toUpperCase(Locale.ROOT);
    }
}
