package com.itop.otel;

import java.time.Instant;

public record SlowTraceModel(
        String serviceName,
        String language,
        String traceId,
        String spanId,
        String spanName,
        long durationMs,
        long elapsedDumpMs,
        Instant occurredAt,
        String threadName,
        String stackTrace
) {
    @Override
    public String toString() {
        return String.format("""
                --------------------------------------------------------------------------------
                [SLOW SPAN DETECTED]
                Service    : %s (%s)
                Name       : %s
                Trace/Span : %s / %s
                Duration   : %,d ms (Dump captured at: %,d ms)
                Thread     : %s
                Time       : %s
                Stack Trace:
                %s
                --------------------------------------------------------------------------------
                """, serviceName, language, spanName, traceId, spanId, durationMs, elapsedDumpMs, threadName, occurredAt, stackTrace);
    }
}