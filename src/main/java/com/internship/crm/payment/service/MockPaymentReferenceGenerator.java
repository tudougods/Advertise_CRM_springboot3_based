package com.internship.crm.payment.service;

import java.util.Locale;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Generates mock provider transaction references outside production. */
@Component
@Profile({"local", "test"})
public class MockPaymentReferenceGenerator {

    public String nextProviderTransactionNo() {
        return "MOCK-TXN-" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .toUpperCase(Locale.ROOT);
    }
}
