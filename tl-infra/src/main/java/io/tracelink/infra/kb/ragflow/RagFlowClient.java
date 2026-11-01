package io.tracelink.infra.kb.ragflow;

import io.tracelink.common.error.BizException;
import io.tracelink.common.error.ErrorCode;
import io.tracelink.domain.kb.remote.KbRemotePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RAGFlow REST 客户端：上传、触发解析、查状态、拉切片、检索。
 * 官方 AgentScope 适配器只覆盖不带标签的检索且不支持上传，所以这几件事自己接。
 * 接口形状以 RAGFlow 0.17 起的 /api/v1 为准，联调时以实际返回为准做容错解析。
 */
@Component
public class RagFlowClient implements KbRemotePort {

    private final String baseUrl;
    private final String apiKey;
    private final RestClient client;

    public RagFlowClient(@Value("${tracelink.ai.ragflow.base-url:}") String baseUrl,
                         @Value("${tracelink.ai.ragflow.api-key:}") String apiKey,
                         @Value("${tracelink.ai.ragflow.timeout-seconds:15}") int timeoutSeconds) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)));
        factory.setReadTimeout(Duration.ofSeconds(Math.max(1, timeoutSeconds * 2L > 120 ? 120 : timeoutSeconds * 2L)));
        this.client = RestClient.builder()
                .baseUrl(this.baseUrl.isEmpty() ? "http://localhost" : this.baseUrl)
                .requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + this.apiKey)
                .build();
    }

    public boolean configured() {
        return !baseUrl.isEmpty() && !apiKey.isEmpty();
    }

    /** 文档入库：返回 RAGFlow 文档标识 */
    public String uploadDocument(String datasetId, String fileName, InputStream content, String title) {
        return call(() -> {
            MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
            parts.add("file", new NamedInputStreamResource(content, fileName));
            Map<?, ?> body = client.post()
                    .uri("/api/v1/datasets/{id}/documents", datasetId)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(parts)
                    .retrieve()
                    .body(Map.class);
            Object data = body == null ? null : body.get("data");
            Map<?, ?> doc = data instanceof List<?> list && !list.isEmpty()
                    ? (Map<?, ?>) list.get(0)
                    : (data instanceof Map<?, ?> map ? map : null);
            if (doc == null || doc.get("id") == null) {
                throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE, "RAGFlow 未返回文档标识");
            }
            return String.valueOf(doc.get("id"));
        });
    }

    public void triggerParse(String datasetId, String documentId) {
        call(() -> {
            client.post()
                    .uri("/api/v1/datasets/{id}/chunks", datasetId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("document_ids", List.of(documentId)))
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
    }

    public ParseState status(String datasetId, String documentId) {
        return call(() -> {
            Map<?, ?> body = client.get()
                    .uri(uri -> uri.path("/api/v1/datasets/{id}/documents").queryParam("id", documentId).build(datasetId))
                    .retrieve()
                    .body(Map.class);
            Object data = body == null ? null : body.get("data");
            Object docs = data instanceof Map<?, ?> map ? map.get("docs") : null;
            if (docs instanceof List<?> list && !list.isEmpty()) {
                Map<?, ?> doc = (Map<?, ?>) list.get(0);
                return new KbRemotePort.ParseState(string(doc.get("run")),
                        incident(doc.get("progress")), string(doc.get("progress_msg")));
            }
            return new KbRemotePort.ParseState("", 0.0, "未取到解析状态");
        });
    }

    public List<RemoteChunk> chunks(String datasetId, String documentId) {
        return call(() -> {
            Map<?, ?> body = client.get()
                    .uri(uri -> uri.path("/api/v1/datasets/{id}/documents/{docId}/chunks")
                            .queryParam("page", 1).queryParam("page_size", 1000)
                            .build(datasetId, documentId))
                    .retrieve()
                    .body(Map.class);
            Object data = body == null ? null : body.get("data");
            Object chunks = data instanceof Map<?, ?> map ? map.get("chunks") : null;
            List<KbRemotePort.RemoteChunk> result = new ArrayList<>();
            if (chunks instanceof List<?> list) {
                int seq = 1;
                for (Object item : list) {
                    Map<?, ?> chunk = (Map<?, ?>) item;
                    result.add(new KbRemotePort.RemoteChunk(string(chunk.get("id")), documentId,
                            string(chunk.get("content")), null, seq++,
                            integer(chunk.get("page_num"))));
                }
            }
            return result;
        });
    }

    public List<RemoteChunk> retrieve(String datasetId, String question, int topK, double threshold,
                                      Map<String, Object> metadataCondition) {
        return call(() -> {
            Map<String, Object> request = new java.util.LinkedHashMap<>();
            request.put("question", question);
            request.put("dataset_ids", List.of(datasetId));
            request.put("top_k", topK);
            request.put("similarity_threshold", threshold);
            request.put("page_size", topK);
            if (metadataCondition != null && !metadataCondition.isEmpty()) {
                request.put("metadata_condition", metadataCondition);
            }
            Map<?, ?> body = client.post()
                    .uri("/api/v1/retrieval")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(Map.class);
            Object data = body == null ? null : body.get("data");
            Object chunks = data instanceof Map<?, ?> map ? map.get("chunks") : null;
            List<KbRemotePort.RemoteChunk> result = new ArrayList<>();
            if (chunks instanceof List<?> list) {
                int seq = 1;
                for (Object item : list) {
                    Map<?, ?> chunk = (Map<?, ?>) item;
                    result.add(new KbRemotePort.RemoteChunk(string(chunk.get("id")), string(chunk.get("document_id")),
                            string(chunk.get("content")), incident(chunk.get("similarity")), seq++,
                            integer(chunk.get("page_num"))));
                }
            }
            return result;
        });
    }

    private <T> T call(java.util.function.Supplier<T> action) {
        if (!configured()) {
            throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE, "RAGFlow 未配置");
        }
        try {
            return action.get();
        } catch (BizException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE, "RAGFlow 调用失败：" + e.getMessage());
        }
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? null : Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static double incident(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? 0.0 : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /** 上传时带上原始文件名，RAGFlow 侧才能按后缀选解析器 */
    private static class NamedInputStreamResource extends InputStreamResource {
        private final String fileName;

        NamedInputStreamResource(InputStream in, String fileName) {
            super(in);
            this.fileName = fileName;
        }

        @Override
        public String getFilename() {
            return fileName;
        }

        @Override
        public long contentLength() {
            return -1;
        }
    }
}
