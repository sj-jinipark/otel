package com.itop.otel;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class SlowTraceFileWriter {

    private static final String LOG_DIR = "./slow_logs";
    private static final int QUEUE_CAPACITY = 10_000; // 부하 대비 큐 버퍼
    private static final BlockingQueue<String> QUEUE = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private static final DateTimeFormatter FILE_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static volatile boolean running = true;
    private static BufferedWriter currentWriter = null;
    private static String currentFileDate = "";

    static {
        File dir = new File(LOG_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // 백그라운드 단일 쓰레드로 디스크 I/O 전담
        Thread worker = new Thread(SlowTraceFileWriter::processQueue, "slow-trace-file-writer");
        worker.setDaemon(true);
        worker.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running = false;
            flushAndClose();
        }));
    }

    /**
     * 리시버 핸들러에서 호출 (비차단)
     */
    public static void asyncWrite(String content) {
        // 큐가 가득 차서 터지는 것을 방지 (초과 시 최신 데이터 유실 방지 또는 드랍 처리)
        boolean offered = QUEUE.offer(content);
        if (!offered) {
            System.err.println("⚠️ [WARN] Slow trace file write queue is FULL! Dropping event.");
        }
    }

    private static void processQueue() {
        List<String> batch = new ArrayList<>(50);

        while (running) {
            try {
                // 데이터 1건 대기 (최대 500ms 대기)
                String item = QUEUE.poll(500, TimeUnit.MILLISECONDS);
                if (item != null) {
                    batch.add(item);
                    // 큐에 더 있으면 최대 50건까지 한 번에 배치 적재
                    QUEUE.drainTo(batch, 49);
                }

                if (!batch.isEmpty()) {
                    writeBatchToFile(batch);
                    batch.clear();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void writeBatchToFile(List<String> batch) {
        try {
            String today = LocalDate.now().format(FILE_DATE_FMT);
            if (!today.equals(currentFileDate) || currentWriter == null) {
                flushAndClose();
                currentFileDate = today;
                File targetFile = new File(LOG_DIR, "slow_traces_" + today + ".log");
                currentWriter = new BufferedWriter(new FileWriter(targetFile, StandardCharsets.UTF_8, true), 32 * 1024);
            }

            for (String str : batch) {
                currentWriter.write(str);
            }
            currentWriter.flush();
        } catch (IOException e) {
            System.err.println(" Failed to write slow trace log to file: " + e.getMessage());
        }
    }

    private static synchronized void flushAndClose() {
        if (currentWriter != null) {
            try {
                currentWriter.flush();
                currentWriter.close();
            } catch (IOException ignored) {}
            currentWriter = null;
        }
    }
}