package com.itop.otel;

import com.google.protobuf.ByteString;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;

import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

public class TraceParser {

    private static final long THREE_SECONDS_NANOS = 3_000_000_000L;
    private static final Set<String> STACK_KEYS = Set.of(
            "slow.stacktrace", "thread.stacktrace", "stacktrace", "exception.stacktrace"
    );

    public static void ingestBatch(ExportTraceServiceRequest request) {
        Set<String> touchedTraceIds = new HashSet<>();

        for (ResourceSpans rs : request.getResourceSpansList()) {
            for (ScopeSpans ss : rs.getScopeSpansList()) {
                for (Span span : ss.getSpansList()) {
                    String traceId = extractTraceId(span);
                    TraceGroup group = TraceManager.getOrCreate(traceId);
                    touchedTraceIds.add(traceId);

                    long startNano = span.getStartTimeUnixNano();
                    long durationNanos = span.getEndTimeUnixNano() - startNano;
                    long durationMs = durationNanos / 1_000_000L;

                    // 1. Root / Server Span 등록
                    if (span.getParentSpanId().isEmpty() || span.getKind() == Span.SpanKind.SPAN_KIND_SERVER) {
                        String route = getStringAttr(span.getAttributesList(), "http.route",
                                getStringAttr(span.getAttributesList(), "url.path", span.getName()));
                        boolean isSlow = durationNanos >= THREE_SECONDS_NANOS;
                        group.setRootInfo(route, startNano, durationMs, isSlow);
                    }

                    // 2. Dump 이벤트 수집
                    boolean dumpFound = false;
                    for (Span.Event ev : span.getEventsList()) {
                        String dump = findMatchingAttr(ev.getAttributesList(), STACK_KEYS);
                        if (dump != null) {
                            long offset = getLongAttr(ev.getAttributesList(), "elapsed_ms", 3000L);
                            long eventTime = ev.getTimeUnixNano() > 0 ? ev.getTimeUnixNano() : (startNano + (offset * 1_000_000L));
                            group.addDump(eventTime, offset, dump);
                            dumpFound = true;
                            break;
                        }
                    }
                    if (!dumpFound) {
                        String dumpAttr = findMatchingAttr(span.getAttributesList(), STACK_KEYS);
                        if (dumpAttr != null) {
                            group.addDump(startNano + 3_000_000_000L, 3000L, dumpAttr);
                        }
                    }

                    // 3. SQL 문 수집
                    String dbStatement = getStringAttr(span.getAttributesList(), "db.statement", null);
                    if (dbStatement != null && !dbStatement.isBlank()) {
                        group.addSql(startNano, dbStatement);
                    }
                }
            }
        }

        // 배치 수신 완료 후 루트 스팬이 완성된 트레이스들을 한 번에 출력
        for (String id : touchedTraceIds) {
            TraceManager.checkAndFlush(id);
        }
    }

    private static String extractTraceId(Span span) {
        String customId = getStringAttr(span.getAttributesList(), "transaction.trace_id", null);
        if (customId != null && !customId.isBlank()) return customId;
        return toHex(span.getTraceId());
    }

    private static String findMatchingAttr(List<KeyValue> attrs, Set<String> targetKeys) {
        for (KeyValue kv : attrs) {
            if (targetKeys.contains(kv.getKey().toLowerCase())) return kv.getValue().getStringValue();
        }
        return null;
    }

    private static String getStringAttr(List<KeyValue> attrs, String key, String defaultVal) {
        for (KeyValue kv : attrs) {
            if (key.equalsIgnoreCase(kv.getKey())) return kv.getValue().getStringValue();
        }
        return defaultVal;
    }

    private static long getLongAttr(List<KeyValue> attrs, String key, long defaultVal) {
        for (KeyValue kv : attrs) {
            if (key.equalsIgnoreCase(kv.getKey())) {
                AnyValue val = kv.getValue();
                return val.hasIntValue() ? val.getIntValue() : defaultVal;
            }
        }
        return defaultVal;
    }

    private static String toHex(ByteString byteString) {
        if (byteString == null || byteString.isEmpty()) return "";
        return HexFormat.of().formatHex(byteString.toByteArray());
    }
}