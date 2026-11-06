package io.tracelink.api.web;

import io.tracelink.api.dto.ChatMessageRequest;
import io.tracelink.api.dto.ChatSessionRequest;
import io.tracelink.api.security.AuthContext;
import io.tracelink.app.chat.ChatAppService;
import io.tracelink.app.chat.ChatAskCommand;
import io.tracelink.app.chat.ChatAskService;
import io.tracelink.app.chat.ChatAskSink;
import io.tracelink.app.chat.ChatDone;
import io.tracelink.app.chat.ChatHint;
import io.tracelink.app.chat.ChatMessageView;
import io.tracelink.app.chat.ChatRetrieval;
import io.tracelink.app.chat.ChatSessionView;
import io.tracelink.app.chat.ChatToolEvent;
import io.tracelink.common.api.ApiResult;
import io.tracelink.app.kb.KbCitation;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** CHT-1 到 CHT-4：会话创建、列表、提问事件流、历史与归档（DESIGN 5.1、5.2） */
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatAppService chatAppService;
    private final ChatAskService chatAskService;

    public ChatController(ChatAppService chatAppService, ChatAskService chatAskService) {
        this.chatAppService = chatAppService;
        this.chatAskService = chatAskService;
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResult<ChatSessionView> create(@RequestBody(required = false) ChatSessionRequest request) {
        ChatSessionRequest body = request == null ? new ChatSessionRequest(null, null, null) : request;
        return ApiResult.ok(chatAppService.create(AuthContext.current(), body.title(), body.scopeType(),
                body.scopeId()));
    }

    @GetMapping("/sessions")
    public ApiResult<List<ChatSessionView>> sessions(@RequestParam(defaultValue = "50") int limit) {
        return ApiResult.ok(chatAppService.list(AuthContext.current(), limit));
    }

    @PostMapping("/sessions/{id}/archive")
    public ApiResult<ChatSessionView> archive(@PathVariable Long id) {
        return ApiResult.ok(chatAppService.archive(AuthContext.current(), id));
    }

    @PostMapping("/sessions/{id}/clear")
    public ApiResult<ChatSessionView> clear(@PathVariable Long id) {
        return ApiResult.ok(chatAppService.clearContext(AuthContext.current(), id));
    }

    @GetMapping("/sessions/{id}/messages")
    public ApiResult<List<ChatMessageView>> messages(@PathVariable Long id,
                                                     @RequestParam(defaultValue = "100") int limit) {
        return ApiResult.ok(chatAppService.messages(AuthContext.current(), id, limit));
    }

    /**
     * 事件流：retrieval → tool_call… → delta… → citations → hint → done；
     * 模型不可用或失败时降级为规则回答，并在 done 里标 degraded，不重试第三次（TC-M5-09）。
     */
    @PostMapping(value = "/sessions/{id}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ask(@PathVariable Long id,
                          @RequestBody ChatMessageRequest request,
                          @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        var scope = request.scope();
        ChatAskCommand command = new ChatAskCommand(request.question(),
                scope == null ? null : scope.deptId(),
                scope == null ? null : scope.taskTypes(),
                request.allowTools());
        var prepared = chatAskService.prepare(AuthContext.current(), id, command, idempotencyKey);
        SseEmitter emitter = new SseEmitter(0L);
        chatAskService.ask(AuthContext.current(), prepared, command, idempotencyKey, new SseChatSink(emitter));
        return emitter;
    }

    /** 把事件流写到 SSE；客户端断开后静默关闭，不影响服务端其它请求 */
    private static final class SseChatSink implements ChatAskSink {

        private final SseEmitter emitter;
        private boolean closed;

        SseChatSink(SseEmitter emitter) {
            this.emitter = emitter;
        }

        @Override
        public void retrieval(ChatRetrieval retrieval) {
            send("retrieval", retrieval);
        }

        @Override
        public void toolCall(ChatToolEvent event) {
            send("tool_call", event);
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
        public void hint(ChatHint hint) {
            send("hint", hint);
        }

        @Override
        public void clarify(List<String> questions) {
            send("clarify", Map.of("questions", questions == null ? List.of() : questions));
        }

        @Override
        public void done(ChatDone done) {
            send("done", done);
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
