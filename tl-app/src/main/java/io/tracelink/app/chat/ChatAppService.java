package io.tracelink.app.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.tracelink.common.error.BizException;
import io.tracelink.common.error.ErrorCode;
import io.tracelink.domain.auth.AuthUser;
import io.tracelink.domain.chat.model.ChatMessage;
import io.tracelink.domain.chat.model.ChatRole;
import io.tracelink.domain.chat.model.ChatScopeType;
import io.tracelink.domain.chat.model.ChatSession;
import io.tracelink.domain.chat.model.ChatSessionStatus;
import io.tracelink.domain.chat.repository.ChatMessageRepository;
import io.tracelink.domain.chat.repository.ChatSessionRepository;
import io.tracelink.domain.org.model.Dept;
import io.tracelink.domain.org.model.Employee;
import io.tracelink.domain.org.repository.DeptRepository;
import io.tracelink.domain.org.repository.EmployeeRepository;
import io.tracelink.domain.org.rules.DataScopeResolver;
import io.tracelink.domain.task.model.TaskScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 会话读写（CHT-1/2/4 与归档）。归属人以外只有本部门管理者可读，
 * 会话只用于承接提问与回答，不写任何业务表（DESIGN 4.8、Q12）。
 */
@Service
public class ChatAppService {

    private static final String ROLE_ADMIN = "SYS_ADMIN";
    private static final String ROLE_MANAGER = "MANAGER";
    private static final int DEFAULT_TITLE_LEN = 40;

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final EmployeeRepository employeeRepository;
    private final DeptRepository deptRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    public ChatAppService(ChatSessionRepository sessionRepository,
                          ChatMessageRepository messageRepository,
                          EmployeeRepository employeeRepository,
                          DeptRepository deptRepository) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.employeeRepository = employeeRepository;
        this.deptRepository = deptRepository;
    }

    @Transactional
    public ChatSessionView create(AuthUser actor, String title, String scopeType, Long scopeId) {
        ChatSession session = new ChatSession();
        session.employeeId = actor.employeeId();
        session.title = (title == null || title.isBlank()) ? "新会话" : trim(title, 120);
        session.scopeType = ChatScopeType.parse(scopeType);
        session.scopeId = resolveScopeId(actor, session.scopeType, scopeId);
        session.status = ChatSessionStatus.OPEN;
        session.msgCount = 0;
        session.deleted = false;
        session.createdAt = LocalDateTime.now();
        sessionRepository.insert(session);
        return toView(session);
    }

    public List<ChatSessionView> list(AuthUser actor, int limit) {
        return sessionRepository.listByEmployee(actor.employeeId(), limit <= 0 ? 50 : limit).stream()
                .map(this::toView)
                .toList();
    }

    public List<ChatMessageView> messages(AuthUser actor, Long sessionId, int limit) {
        ChatSession session = sessionRepository.findById(sessionId);
        if (session == null) {
            throw BizException.notFound("会话不存在或已删除");
        }
        if (!canRead(actor, session)) {
            throw new BizException(ErrorCode.OUT_OF_SCOPE, "该会话不属于你，也不在你的管理范围内");
        }
        return messageRepository.listBySession(sessionId, limit <= 0 ? 100 : limit).stream()
                .map(this::toMessageView)
                .toList();
    }

    @Transactional
    public ChatSessionView archive(AuthUser actor, Long sessionId) {
        ChatSession session = requireOwned(actor, sessionId);
        if (session.status == ChatSessionStatus.ARCHIVED) {
            return toView(session);
        }
        sessionRepository.updateStatus(sessionId, ChatSessionStatus.ARCHIVED);
        session.status = ChatSessionStatus.ARCHIVED;
        return toView(session);
    }

    /** 归属校验：提问与归档都只允许本人 */
    public ChatSession requireOwned(AuthUser actor, Long sessionId) {
        ChatSession session = sessionRepository.findById(sessionId);
        if (session == null) {
            throw BizException.notFound("会话不存在或已删除");
        }
        if (!Objects.equals(actor.employeeId(), session.employeeId)) {
            throw new BizException(ErrorCode.OUT_OF_SCOPE, "该会话不属于你");
        }
        return session;
    }

    /** 提问前后写消息与推进活动时间；标题在首轮提问后固化 */
    public void appendMessage(ChatSession session, ChatRole role, String content) {
        ChatMessage message = new ChatMessage();
        message.sessionId = session.id;
        message.role = role;
        message.content = content;
        message.createdAt = LocalDateTime.now();
        messageRepository.insert(message);
    }

    public void touch(ChatSession session, String title, int msgCount) {
        sessionRepository.updateActivity(session.id, title, msgCount, LocalDateTime.now());
        session.msgCount = msgCount;
        session.lastMsgAt = LocalDateTime.now();
    }

    public String titleOf(String question) {
        String trimmed = question == null ? "" : question.trim();
        return trimmed.length() <= DEFAULT_TITLE_LEN ? trimmed : trimmed.substring(0, DEFAULT_TITLE_LEN);
    }

    public ChatMessageView toMessageView(ChatMessage message) {
        return new ChatMessageView(message.id, message.role.name(), message.role.label(), message.content,
                parseJson(message.citations), parseJson(message.toolCalls), message.modelName,
                message.promptVer, message.tokenIn, message.tokenOut, message.latencyMs, message.createdAt);
    }

    private ChatSessionView toView(ChatSession session) {
        String scopeName = null;
        if (session.scopeId != null) {
            Dept dept = deptRepository.findById(session.scopeId);
            scopeName = dept == null ? null : dept.getName();
        }
        ChatScopeType type = session.scopeType == null ? ChatScopeType.DEPT : session.scopeType;
        return new ChatSessionView(session.id, session.title, type.name(), type.label(), session.scopeId,
                session.msgCountValue(), session.lastMsgAt, session.statusValue(),
                session.status == null ? ChatSessionStatus.OPEN.label() : session.status.label(),
                session.createdAt);
    }

    private boolean canRead(AuthUser actor, ChatSession session) {
        if (Objects.equals(actor.employeeId(), session.employeeId)) {
            return true;
        }
        if (actor.roles() == null || !(actor.roles().contains(ROLE_ADMIN) || actor.roles().contains(ROLE_MANAGER))) {
            return false;
        }
        Employee owner = employeeRepository.findById(session.employeeId);
        if (owner == null || owner.getDeptId() == null) {
            return false;
        }
        TaskScope scope = DataScopeResolver.resolve(actor, deptRepository.findAll());
        return scope.deptIds() == null || scope.deptIds().contains(owner.getDeptId());
    }

    private Long resolveScopeId(AuthUser actor, ChatScopeType type, Long scopeId) {
        if (type == ChatScopeType.ALL || type == ChatScopeType.SELF) {
            return null;
        }
        TaskScope scope = DataScopeResolver.resolve(actor, deptRepository.findAll());
        if (scopeId != null && (scope.deptIds() == null || scope.deptIds().contains(scopeId)) && deptRepository.findById(scopeId) != null) {
            return scopeId;
        }
        return actor.deptId();
    }

    private Object parseJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            return mapper.readValue(raw, Object.class);
        } catch (IOException e) {
            return Map.of("raw", raw);
        }
    }

    private String trim(String value, int max) {
        String text = value.trim();
        return text.length() <= max ? text : text.substring(0, max);
    }
}
