-- 七日提醒会同时发给承担者与负责人，同一缺口的同一级别因此有多个收件人，
-- 幂等键必须带上 recipient_id，否则第二个收件人插入时撞唯一键。
ALTER TABLE notification
  DROP INDEX uk_notify_episode,
  ADD UNIQUE KEY uk_notify_episode (task_id, gate_code, level, episode_start, recipient_id);
