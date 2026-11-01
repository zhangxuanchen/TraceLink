package io.tracelink.api.web;

import io.tracelink.api.dto.KbAskRequest;
import io.tracelink.api.dto.KbSearchRequest;
import io.tracelink.api.security.AuthContext;
import io.tracelink.app.kb.KbAppService;
import io.tracelink.app.kb.KbAskCommand;
import io.tracelink.app.kb.KbAskService;
import io.tracelink.app.kb.KbAskSink;
import io.tracelink.app.kb.KbChunkView;
import io.tracelink.app.kb.KbCitation;
import io.tracelink.app.kb.KbDocumentView;
import io.tracelink.app.kb.KbHistoryResult;
import io.tracelink.app.kb.KbSearchView;
import io.tracelink.app.kb.KbSyncView;
import io.tracelink.app.kb.KbUploadCommand;
import io.tracelink.common.api.ApiResult;
import io.tracelink.common.error.BizException;
import io.tracelink.domain.auth.AuthUser;
import io.tracelink.domain.kb.model.KbDomain;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** KB-1 到 KB-5：文档上传、检索、带引用问答、同步状态、切片回溯（DESIGN 5.1、5.2） */
@RestController
@RequestMapping("/api/v1/kb")
public class KbController {

    private final KbAppService kbAppService;
    private final KbAskService kbAskService;

    public KbController(KbAppService kbAppService, KbAskService kbAskService) {
        this.kbAppService = kbAppService;
        this.kbAskService = kbAskService;
    }

    @PostMapping("/documents")
    public ApiResult<KbDocumentView> upload(@RequestPart("file") MultipartFile file,
                                            @RequestParam(required = false) String title,
                                            @RequestParam(required = false) String domain,
                                            @RequestParam(required = false) Long deptId,
                                            @RequestParam(required = false) String visibility) {
        if (file == null || file.isEmpty()) {
            throw BizException.invalid("请选择要上传的文件");
        }
        try {
            return ApiResult.ok(kbAppService.upload(AuthContext.current(),
                    new KbUploadCommand(title, domain, deptId, visibility,
                            file.getOriginalFilename(), "UPLOAD"),
                    file.getInputStream()));
        } catch (IOException e) {
            throw BizException.invalid("文件读取失败：" + e.getMessage());
        }
    }

    @GetMapping("/documents")
    public ApiResult<List<KbDocumentView>> list(@RequestParam(required = false) String domain,
                                                @RequestParam(required = false) Long deptId,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(required = false) String keyword) {
        return ApiResult.ok(kbAppService.list(AuthContext.current(), domain, deptId, status, keyword));
    }

    @GetMapping("/documents/{id}/sync")
    public ApiResult<KbSyncView> sync(@PathVariable Long id) {
        return ApiResult.ok(kbAppService.sync(AuthContext.current(), id));
    }

    @PostMapping("/documents/{id}/sync")
    public ApiResult<KbSyncView> reparse(@PathVariable Long id) {
        return ApiResult.ok(kbAppService.reparse(AuthContext.current(), id));
    }

    @PostMapping("/documents/history")
    public ApiResult<KbHistoryResult> history(@RequestParam(required = false) Long deptId,
                                              @RequestParam(defaultValue = "100") int limit) {
        return ApiResult.ok(kbAppService.historySync(AuthContext.current(), deptId, limit));
    }

    @PostMapping("/search")
    public ApiResult<KbSearchView> search(@RequestBody KbSearchRequest request) {
        return ApiResult.ok(kbAppService.search(AuthContext.current(), request.question(),
                request.domain(), request.deptId(), request.topK()));
    }

    @GetMapping("/chunks/{id}")
    public ApiResult<KbChunkView> chunk(@PathVariable Long id) {
        return ApiResult.ok(kbAppService.chunk(AuthContext.current(), id));
    }

    /** 事件流：retrieved → delta… → citations → done；模型不可用时只发 error 再关闭 */
    @PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ask(@RequestBody KbAskRequest request) {
        AuthUser actor = AuthContext.current();
        KbDomain domain = request.domain() == null || request.domain().isBlank()
                ? null : KbDomain.parse(request.domain());
        int topK = request.topK() == null ? 6 : request.topK();
        KbAskCommand command = new KbAskCommand(request.question(), domain, request.deptId(), topK);
        kbAskService.validate(command);
        SseEmitter emitter = new SseEmitter(0L);
        kbAskService.ask(actor, command, new SseAskSink(emitter));
        return emitter;
    }

    /** 把事件流写到 SSE；客户端断开后静默关闭，不影响服务端其它请求 */
    private static final class SseAskSink implements KbAskSink {

        private final SseEmitter emitter;
        private boolean closed;

        SseAskSink(SseEmitter emitter) {
            this.emitter = emitter;
        }

        @Override
        public void retrieved(KbSearchView result) {
            send("retrieved", result);
        }

        @Override
        public void delta(String text) {
            send("delta", Map.of("text", text == null ? "" : text));
        }

        @Override
        public void citations(List<KbCitation> citations) {
            send("citations", citations);
        }

        @Override
        public void done(int answerChars) {
            send("done", Map.of("answerChars", answerChars));
            finish();
        }

        @Override
        public void error(String code, String message) {
            send("error", Map.of("code", code, "message", message == null ? "" : message));
            finish();
        }

        private synchronized void send(String name, Object data) {
            if (closed) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
            } catch (IOException e) {
                closed = true;
            }
        }

        private synchronized void finish() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                emitter.complete();
            } catch (RuntimeException ignored) {
                // 客户端已断开时 complete 会抛异常，这里不再处理
            }
        }
    }
}
