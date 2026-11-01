-- 采纳结果记录动作对象与时间，原来是 VARCHAR(64) 放不下 JSON，这里放宽（TC-M5-08）
ALTER TABLE ai_suggestion
  MODIFY COLUMN accept_result VARCHAR(512) NULL COMMENT '采纳结果 JSON：动作类型、对象类型、对象 ID 与编号、采纳时间';
