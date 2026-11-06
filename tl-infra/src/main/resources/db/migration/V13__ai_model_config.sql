-- 模型配置页：火山方舟的推理密钥与对话模型标识落 sys_config，页面保存后智能层每轮读取即生效
-- 初始值为空，回落启动时的环境变量（ARK_API_KEY / ARK_CHAT_MODEL / ARK_BASE_URL）
INSERT INTO sys_config (cfg_key, cfg_value, value_type, group_code, remark) VALUES
  ('ai.ark.api-key',    '', 'STRING', 'AI', '火山方舟推理密钥；页面只回显掩码，留空保存即沿用当前值或环境变量'),
  ('ai.ark.chat.model', '', 'STRING', 'AI', '对话模型标识或推理接入点 ID，形如 ep-...，以火山方舟控制台为准'),
  ('ai.ark.base-url',   'https://ark.cn-beijing.volces.com/api/v3', 'STRING', 'AI', 'Ark 接口根地址，换区域时改这里');
