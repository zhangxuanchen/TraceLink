<template>
  <section class="ai-config">
    <div class="page-head">
      <div>
        <h2 class="page-title">模型配置</h2>
        <p class="page-sub">火山方舟的 AK 与模型 ID；保存后下一轮生效</p>
      </div>
      <div class="head-actions">
        <span class="tag ai-state" :class="config?.configured ? 'tag-ok' : 'tag-bad'">
          {{ config?.configured ? '已配置' : '未配置' }}
        </span>
        <span class="muted ai-source">来源 · {{ config?.source || '—' }}</span>
        <button class="btn" :disabled="loading" @click="load">刷新</button>
      </div>
    </div>

    <div v-if="error" class="form-error">{{ error }}</div>
    <div v-if="loading && !config" class="card empty">加载中…</div>

    <template v-else-if="config">
      <div class="card ai-card">
        <div class="card-head">
          <h3>火山引擎方舟</h3>
          <span v-if="!config.editable" class="tag">只读 · 仅系统管理员可改</span>
        </div>

        <div class="ai-form">
          <div class="field">
            <label for="ai-base-url">接口地址</label>
            <input id="ai-base-url" v-model="form.baseUrl" class="ai-base-url"
                   :disabled="!config.editable || saving || testing"
                   placeholder="https://ark.cn-beijing.volces.com/api/v3" />
          </div>
          <div class="field">
            <label for="ai-model">模型 ID 或推理接入点</label>
            <input id="ai-model" v-model="form.chatModel" class="ai-model"
                   :disabled="!config.editable || saving || testing"
                   placeholder="形如 ep-... 或控制台给出的模型标识" />
          </div>
          <div class="field">
            <label for="ai-key">API Key（AK）</label>
            <input id="ai-key" v-model="form.apiKey" class="ai-key" type="password"
                   autocomplete="new-password"
                   :disabled="!config.editable || saving || testing"
                   :placeholder="config.apiKeyConfigured
                     ? `已保存 ${config.apiKeyMasked}，留空则沿用`
                     : '粘贴火山方舟的推理密钥'" />
          </div>
        </div>

        <p class="ai-mask muted">
          当前密钥：{{ config.apiKeyConfigured ? config.apiKeyMasked : '未配置' }}
          <span v-if="config.updatedAt">· 最后修改 {{ config.updatedAt }}</span>
        </p>

        <div class="ai-actions">
          <button class="btn btn-primary ai-save"
                  :disabled="!config.editable || saving || testing" @click="save">
            {{ saving ? '保存中…' : '保存并生效' }}
          </button>
          <button class="btn ai-test" :disabled="saving || testing || !config.configured" @click="test">
            {{ testing ? '自检中…' : '用已保存配置自检' }}
          </button>
          <span class="muted">自检只发一条 8 token 的 ping，不读工单数据</span>
        </div>

        <div v-if="notice" class="ai-notice">{{ notice }}</div>

        <div v-if="testResult" class="ai-test-result" :class="{ 'is-bad': !testResult.ok }">
          <div class="ai-test-head">
            <span class="tag" :class="testResult.ok ? 'tag-ok' : 'tag-bad'">
              {{ testResult.ok ? '连通' : '不通' }}
            </span>
            <span class="num">{{ testResult.model || '—' }}</span>
            <span v-if="testResult.status" class="muted num">HTTP {{ testResult.status }}</span>
            <span class="muted num">{{ testResult.latencyMs }} ms</span>
          </div>
          <p class="ai-test-message">{{ testResult.message }}</p>
        </div>
      </div>

      <div class="card ai-help">
        <div class="card-head">
          <h3>怎么填</h3>
        </div>
        <ol class="ai-steps">
          <li>在火山引擎方舟控制台开通对话模型，复制推理密钥到上面的 API Key。</li>
          <li>模型 ID 填控制台给出的模型标识或推理接入点 ID（形如 <span class="num">ep-...</span>），两种以控制台为准。</li>
          <li>密钥只按末四位回显，明文不出接口；要把密钥留在环境变量里，把这里留空即可沿用启动时的值。</li>
          <li>向量与重排模型由 RAGFlow 侧配置，不在这页改。</li>
        </ol>
      </div>
    </template>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { aiApi } from '../api/ai.js'

const loading = ref(true)
const saving = ref(false)
const testing = ref(false)
const error = ref('')
const notice = ref('')
const config = ref(null)
const testResult = ref(null)
const form = reactive({ baseUrl: '', chatModel: '', apiKey: '' })

async function load() {
  loading.value = true
  error.value = ''
  notice.value = ''
  testResult.value = null
  try {
    config.value = await aiApi.config()
    form.baseUrl = config.value.baseUrl || ''
    form.chatModel = config.value.chatModel || ''
    form.apiKey = ''
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

async function save() {
  error.value = ''
  notice.value = ''
  testResult.value = null
  saving.value = true
  try {
    config.value = await aiApi.updateConfig({
      baseUrl: form.baseUrl,
      chatModel: form.chatModel,
      apiKey: form.apiKey
    })
    form.apiKey = ''
    notice.value = '已保存，下一轮对话按新配置调用'
  } catch (e) {
    error.value = e.message
  } finally {
    saving.value = false
  }
}

async function test() {
  error.value = ''
  testing.value = true
  testResult.value = null
  try {
    testResult.value = await aiApi.test()
  } catch (e) {
    error.value = e.message
  } finally {
    testing.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.head-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.muted {
  color: var(--txt-3);
  font-size: 12px;
}

.ai-card {
  padding: 16px 18px;
}

.ai-form {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 14px 18px;
  margin-top: 14px;
}

.ai-form input {
  width: 100%;
}

.ai-mask {
  margin: 12px 0 0;
}

.ai-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 16px;
}

.ai-notice {
  margin-top: 12px;
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  background: rgba(46, 125, 50, 0.08);
  color: var(--ok, #2E7D32);
  font-size: 13px;
}

.ai-test-result {
  margin-top: 12px;
  padding: 10px 12px;
  border: 1px solid var(--divider);
  border-radius: var(--radius-sm);
  background: #FAFBFC;
}

.ai-test-result.is-bad {
  border-color: rgba(198, 40, 40, 0.35);
  background: rgba(198, 40, 40, 0.04);
}

.ai-test-head {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
}

.ai-test-message {
  margin: 8px 0 0;
  color: var(--txt-2);
  font-size: 12px;
  line-height: 1.6;
  word-break: break-all;
}

.ai-help {
  margin-top: 14px;
  padding: 16px 18px;
}

.ai-steps {
  margin: 10px 0 0;
  padding-left: 20px;
  color: var(--txt-2);
  font-size: 13px;
  line-height: 1.9;
}
</style>
