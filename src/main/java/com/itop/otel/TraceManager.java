package com.itop.otel;

import java.util.Map;
import java.util.concurrent.*;

public class TraceManager {
    private static final Map<String, TraceGroup> ACTIVE_TRACES = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService CLEANER = Executors.newSingleThreadScheduledExecutor();
    private static final long FILE_WRITE_THRESHOLD_MS = 4000L; // 4초 기준

    public static TraceGroup getOrCreate(String traceId) {
        return ACTIVE_TRACES.computeIfAbsent(traceId, id -> {
            TraceGroup tg = new TraceGroup(id);
            CLEANER.schedule(() -> flushIfPresent(id), 15, TimeUnit.SECONDS);
            return tg;
        });
    }

    public static void checkAndFlush(String traceId) {
        TraceGroup group = ACTIVE_TRACES.get(traceId);
        if (group != null && group.isRootCompleted()) {
            flushIfPresent(traceId);
        }
    }

    private static synchronized void flushIfPresent(String traceId) {
        TraceGroup removed = ACTIVE_TRACES.remove(traceId);
        if (removed != null) {
            String renderedTree = removed.renderTree();

            // 1. 기존 동작: 콘솔 출력은 그대로 유지
            System.out.print(renderedTree);

            // 2. 추가 동작: 총 소요시간이 4초 이상인 경우에만 비동기 큐로 전달
            if (removed.getTotalDurationMs() >= FILE_WRITE_THRESHOLD_MS) {
                SlowTraceFileWriter.asyncWrite(renderedTree);
            }
        }
    }
}