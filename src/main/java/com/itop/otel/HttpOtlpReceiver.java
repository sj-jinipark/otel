package com.itop.otel;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;

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

                    // ⭐ 원시 데이터 전체 덤프 출력
                    //System.out.println("\n################### [OTLP RAW PROTO DUMP START] ###################");
                    //System.out.println(request.toString());
                    //System.out.println("################### [OTLP RAW PROTO DUMP END] #####################\n");
                    
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