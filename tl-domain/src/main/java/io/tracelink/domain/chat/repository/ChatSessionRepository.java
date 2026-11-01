package io.tracelink.domain.chat.repository;

import io.tracelink.domain.chat.model.ChatSession;
import io.tracelink.domain.chat.model.ChatSessionStatus;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatSessionRepository {

    ChatSession findById(Long id);

    /** 我的会话，按最后活动时间倒序；归档的仍返回，状态由页面标注 */
    List<ChatSession> listByEmployee(Long employeeId, int limit);

    void insert(ChatSession session);

    /** 只更新活动字段：标题在首轮回答后固化，消息数与最后活动时间每轮推进 */
    void updateActivity(Long id, String title, int msgCount, LocalDateTime lastMsgAt);

    void updateStatus(Long id, ChatSessionStatus status);
}
