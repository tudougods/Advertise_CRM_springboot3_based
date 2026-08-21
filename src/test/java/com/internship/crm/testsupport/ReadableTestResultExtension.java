package com.internship.crm.testsupport;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

/**
 * Prints one concise, human-readable result line after each JUnit test.
 */
public class ReadableTestResultExtension implements TestWatcher {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final PrintStream UTF8_OUT = new PrintStream(System.out, true, StandardCharsets.UTF_8);

    @Override
    public void testSuccessful(ExtensionContext context) {
        printResult(context, "通过");
    }

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        printResult(context, "失败（" + cause.getClass().getSimpleName() + "）");
    }

    @Override
    public void testAborted(ExtensionContext context, Throwable cause) {
        printResult(context, "中止（" + cause.getClass().getSimpleName() + "）");
    }

    @Override
    public void testDisabled(ExtensionContext context, Optional<String> reason) {
        printResult(context, "跳过" + reason.map(value -> "（" + value + "）").orElse(""));
    }

    private void printResult(ExtensionContext context, String result) {
        int testNumber = SEQUENCE.incrementAndGet();
        String testGroup = context.getParent()
                .map(ExtensionContext::getDisplayName)
                .orElse("测试");

        UTF8_OUT.printf(
                "test%02d-%s：%s%s%n",
                testNumber,
                testGroup,
                context.getDisplayName(),
                result);
    }
}
