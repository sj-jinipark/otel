package com.itop.otel;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
// ⭐ 아래 import 추가
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import io.opentelemetry.proto.common.v1.KeyValue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class HttpOtlpReceiver {

    public static void main(String[] args) throws IOException {
        int port = 4318;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/v1/traces", new TraceHandler());
        server.setExecutor(null);

        System.out.println(">>> OTLP Trace Receiver started on port " + port);
        server.start();
    }

    static class TraceHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                try (InputStream is = exchange.getRequestBody()) {
                    ExportTraceServiceRequest request = ExportTraceServiceRequest.parseFrom(is.readAllBytes());

                    // =========================================================================
                    // ⭐ [디버깅 로그] OTLP 수신 원본 데이터 검사
                    // =========================================================================
                    System.out.println("\n================ [OTLP RAW PACKET RECEIVED] ================");
                    for (ResourceSpans rs : request.getResourceSpansList()) {
                        for (ScopeSpans ss : rs.getScopeSpansList()) {
                            System.out.println(" InstrumentationScope: " + ss.getScope().getName() + ", Spans Count: " + ss.getSpansCount());

                            for (Span span : ss.getSpansList()) {
                                System.out.println("\n  - Span Name: " + span.getName());
                                System.out.println("     Kind: " + span.getKind());
                                System.out.println("     TraceId: " + bytesToHex(span.getTraceId().toByteArray()));
                                System.out.println("     SpanId : " + bytesToHex(span.getSpanId().toByteArray()));
                                System.out.println("     ParentSpanId: " + bytesToHex(span.getParentSpanId().toByteArray()));

                                // 1. Span Attributes(태그) 확인
                                for (KeyValue attr : span.getAttributesList()) {
                                    System.out.println("      [Attr] " + attr.getKey() + " = " + attr.getValue().getStringValue());
                                }

                                // 2. Span Events(스택 덤프) 확인
                                for (Span.Event event : span.getEventsList()) {
                                    System.out.println("      [Event] " + event.getName());
                                    for (KeyValue evAttr : event.getAttributesList()) {
                                        System.out.println("        └─ " + evAttr.getKey() + " : " + evAttr.getValue().getStringValue());
                                    }
                                }
                            }
                        }
                    }
                    System.out.println("============================================================\n");

                    // 기존 비동기 누적 병합 처리
                    TraceParser.ingestBatch(request);

                    byte[] response = new byte[0];
                    exchange.getResponseHeaders().set("Content-Type", "application/x-protobuf");
                    exchange.sendResponseHeaders(200, response.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    exchange.sendResponseHeaders(500, -1);
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }

        // TraceId/SpanId ByteString -> Hex 변환 헬퍼
        private static String bytesToHex(byte[] bytes) {
            if (bytes == null || bytes.length == 0) return "root (none)";
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        }
    }
}