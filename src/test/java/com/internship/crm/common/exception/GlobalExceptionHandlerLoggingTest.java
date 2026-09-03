package com.internship.crm.common.exception;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.internship.crm.testsupport.ReadableTestResultExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;

@DisplayName("全局异常日志级别")
@ExtendWith(ReadableTestResultExtension.class)
class GlobalExceptionHandlerLoggingTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final Logger logger =
            (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Level originalLevel;

    @BeforeEach
    void captureLogs() {
        originalLevel = logger.getLevel();
        logger.setLevel(Level.TRACE);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void restoreLogger() {
        logger.detachAppender(appender);
        appender.stop();
        logger.setLevel(originalLevel);
    }

    @Test
    @DisplayName("普通参数或资源不存在属于可预期 DEBUG 事件")
    void expectedClientFailuresUseDebug() {
        handler.handleBusinessException(new BusinessException(CommonErrorCode.NOT_FOUND));

        ILoggingEvent event = onlyEvent();
        assertAll(
                () -> assertEquals(Level.DEBUG, event.getLevel()),
                () -> assertEquals(
                        "Expected business request rejection: code=COMMON_NOT_FOUND status=404",
                        event.getFormattedMessage()),
                () -> assertNull(event.getThrowableProxy()));
    }

    @Test
    @DisplayName("业务冲突属于需要关注的 WARN 事件")
    void businessConflictsUseWarn() {
        handler.handleBusinessException(new BusinessException(CommonErrorCode.CONFLICT));

        ILoggingEvent event = onlyEvent();
        assertAll(
                () -> assertEquals(Level.WARN, event.getLevel()),
                () -> assertEquals(
                        "Business request rejected: code=COMMON_CONFLICT status=409",
                        event.getFormattedMessage()),
                () -> assertNull(event.getThrowableProxy()));
    }

    @Test
    @DisplayName("已识别的服务端业务故障保留一次 ERROR 堆栈")
    void serverSideBusinessFailuresUseErrorWithThrowable() {
        BusinessException failure = new BusinessException(
                CommonErrorCode.INTERNAL_ERROR,
                new IllegalStateException("configuration unavailable"));

        handler.handleBusinessException(failure);

        ILoggingEvent event = onlyEvent();
        assertAll(
                () -> assertEquals(Level.ERROR, event.getLevel()),
                () -> assertEquals(
                        "Business operation failed: code=COMMON_INTERNAL_ERROR status=500",
                        event.getFormattedMessage()),
                () -> assertNotNull(event.getThrowableProxy()));
    }

    @Test
    @DisplayName("未知系统异常只记录一次完整 ERROR")
    void unexpectedFailuresProduceOneErrorWithThrowable() {
        handler.handleUnexpectedException(new IllegalStateException("database unavailable"));

        ILoggingEvent event = onlyEvent();
        assertAll(
                () -> assertEquals(Level.ERROR, event.getLevel()),
                () -> assertEquals("Unhandled application exception", event.getFormattedMessage()),
                () -> assertNotNull(event.getThrowableProxy()));
    }

    private ILoggingEvent onlyEvent() {
        assertEquals(1, appender.list.size());
        return appender.list.get(0);
    }
}
