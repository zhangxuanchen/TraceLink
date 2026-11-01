package io.tracelink.domain.gate.repository;

import io.tracelink.domain.gate.model.GateCode;
import io.tracelink.domain.gate.model.Notification;

import java.time.LocalDate;
import java.util.List;

public interface NotificationRepository {

    /** 同一收件人、同一缺口、同一级别、同一周期起点只发一次 */
    boolean exists(Long taskId, GateCode gateCode, String level, LocalDate episodeStart, Long recipientId);

    void insert(Notification notification);

    List<Notification> listByRecipient(Long recipientId, boolean unreadOnly, int limit);

    boolean markRead(Long id, Long recipientId);

    long countUnread(Long recipientId);
}
