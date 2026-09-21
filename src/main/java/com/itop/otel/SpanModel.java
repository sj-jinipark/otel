package com.itop.otel;

import java.time.Instant;

public class SpanModel {
    private final String serviceName;
    private final String language;
    private final String traceId;
    private final String spanId;
    private final String parentSpanId;
    private final String spanName;
    private final String spanKind;
    private final long durationMs;
    private final Instant startTime;
    private final String threadName;
    private final String httpMethod;
    private final String httpRoute;
    private final Integer httpStatusCode;
    private final String dbStatement;
    private final boolean isSlow;
    private final long elapsedDumpMs;
    private final String stackTrace;

    public SpanModel(String serviceName, String language, String traceId, String spanId, 
                     String parentSpanId, String spanName, String spanKind, long durationMs, 
                     Instant startTime, String threadName, String httpMethod, String httpRoute, 
                     Integer httpStatusCode, String dbStatement, boolean isSlow, 
                     long elapsedDumpMs, String stackTrace) {
        this.serviceName = serviceName;
        this.language = language;
        this.traceId = traceId;
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.spanName = spanName;
        this.spanKind = spanKind;
        this.durationMs = durationMs;
        this.startTime = startTime;
        this.threadName = threadName;
        this.httpMethod = httpMethod;
        this.httpRoute = httpRoute;
        this.httpStatusCode = httpStatusCode;
        this.dbStatement = dbStatement;
        this.isSlow = isSlow;
        this.elapsedDumpMs = elapsedDumpMs;
        this.stackTrace = stackTrace;
    }

    public boolean isSlow() { return isSlow; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        if (isSlow) {
            sb.append("\n============================= [SLOW SPAN DETECTED] =============================\n");
        } else {
            sb.append("------------------------------------------------------------------------------------\n");
        }

        sb.append(String.format("[%s] Service: %s (%s) | Kind: %s\n", 
                isSlow ? "SLOW" : "INFO", serviceName, language, spanKind));
        sb.append(String.format("Name       : %s\n", spanName));
        sb.append(String.format("Trace/Span : %s / %s", traceId, spanId));
        if (!parentSpanId.isEmpty()) {
            sb.append(String.format(" (Parent: %s)", parentSpanId));
        }
        sb.append("\n");
        sb.append(String.format("Duration   : %,d ms | Thread: %s | Time: %s\n", durationMs, threadName, startTime));

        // HTTP 속성이 있으면 출력
        if (httpMethod != null || httpRoute != null) {
            sb.append(String.format("HTTP       : %s %s (Status: %s)\n", 
                    httpMethod != null ? httpMethod : "-", 
                    httpRoute != null ? httpRoute : "-", 
                    httpStatusCode != null ? httpStatusCode : "-"));
        }

        // DB 쿼리가 있으면 출력
        if (dbStatement != null && !dbStatement.isBlank()) {
            sb.append(String.format("DB Query   : %s\n", dbStatement.replaceAll("\\s+", " ").trim()));
        }

        // 지연 스택 트레이스가 잡힌 경우 출력
        if (stackTrace != null && !stackTrace.isBlank()) {
            sb.append(String.format("Dump Event : Captured at %,d ms\n", elapsedDumpMs));
            sb.append("Stack Trace:\n").append(stackTrace);
        }

        if (isSlow) {
            sb.append("====================================================================================\n");
        }

        return sb.toString();
    }
}