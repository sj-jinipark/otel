package com.itop.otel;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TraceGroup {
    private static final DateTimeFormatter TIME_FORMATTER = 
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private final String traceId;
    private String rootPath = "UNKNOWN";
    private long rootStartTimeNano = 0;
    private long totalDurationMs = 0;
    private boolean slow = false;
    private boolean rootCompleted = false;

    private final List<TimelineItem> items = new ArrayList<>();

    public TraceGroup(String traceId) {
        this.traceId = traceId;
    }

    public synchronized void setRootInfo(String path, long startTimeNano, long durationMs, boolean isSlow) {
        this.rootPath = path;
        this.rootStartTimeNano = startTimeNano;
        this.totalDurationMs = durationMs;
        this.slow = isSlow;
        this.rootCompleted = true; // 루트 요청 도착 완료
    }

    public synchronized void addDump(long timeNano, long dumpOffsetMs, String stackTrace) {
        items.add(new TimelineItem(timeNano, ItemType.DUMP, dumpOffsetMs, stackTrace));
    }

    public synchronized void addSql(long timeNano, String sql) {
        items.add(new TimelineItem(timeNano, ItemType.SQL, 0, sql));
    }

    public boolean isRootCompleted() {
        return rootCompleted;
    }

    public boolean isSlow() {
        return slow || items.stream().anyMatch(i -> i.type == ItemType.DUMP);
    }

    public synchronized String renderTree() {
        items.sort(Comparator.comparingLong(t -> t.timeNano));

        StringBuilder sb = new StringBuilder();
        sb.append(" [TRACE ID]: ").append(traceId).append("\n");

        long baseNano = (rootStartTimeNano > 0) ? rootStartTimeNano : 
                (!items.isEmpty() ? items.get(0).timeNano : 0);

        String rootTimeStr = baseNano > 0 ? 
                TIME_FORMATTER.format(Instant.ofEpochMilli(baseNano / 1_000_000L)) : "-";

        if (isSlow()) {
            sb.append(String.format(" [%s] [SLOW REQUEST DETECTED (>= 3000ms)]: %s (Total: %,d ms)\n", 
                    rootTimeStr, rootPath, totalDurationMs));
        } else {
            sb.append(String.format(" [%s] [REQUEST]: %s (Total: %,d ms)\n", 
                    rootTimeStr, rootPath, totalDurationMs));
        }

        long prevNano = baseNano;
        int sqlIndex = 1;
        int total = items.size();

        for (int i = 0; i < total; i++) {
            TimelineItem item = items.get(i);
            boolean isLast = (i == total - 1);
            String branch = isLast ? "   └── " : "   ├── ";

            String timeStr = TIME_FORMATTER.format(Instant.ofEpochMilli(item.timeNano / 1_000_000L));
            double elapsedMs = (item.timeNano - baseNano) / 1_000_000.0;
            double gapMs = (item.timeNano - prevNano) / 1_000_000.0;
            prevNano = item.timeNano;

            String prefix = String.format("[%s] [+%,6.1fms / gap:+%,5.1fms] ", timeStr, elapsedMs, gapMs);

            if (item.type == ItemType.DUMP) {
                sb.append(branch).append(prefix)
                  .append(String.format(" [SLOW CALL STACK DUMP (at %,d ms)]\n", item.dumpOffsetMs));

                String indent = isLast ? "       " : "   │   ";
                for (String line : item.content.split("\n")) {
                    if (!line.isBlank()) {
                        sb.append(indent).append("- ").append(line.trim()).append("\n");
                    }
                }
            } else {
                sb.append(branch).append(prefix)
                  .append(" [SQL ").append(sqlIndex++).append("] ")
                  .append(item.content.replaceAll("\\s+", " ").trim())
                  .append("\n");
            }
        }

        sb.append("=======================================================\n");
        return sb.toString();
    }

    private enum ItemType { DUMP, SQL }

    private static class TimelineItem {
        final long timeNano;
        final ItemType type;
        final long dumpOffsetMs;
        final String content;

        TimelineItem(long timeNano, ItemType type, long dumpOffsetMs, String content) {
            this.timeNano = timeNano;
            this.type = type;
            this.dumpOffsetMs = dumpOffsetMs;
            this.content = content;
        }
    }
    
    public long getTotalDurationMs() {
        return totalDurationMs;
    }
}