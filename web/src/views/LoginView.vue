<template>
  <div class="login">
    <form class="card login-card" @submit.prevent="submit">
      <div class="head">
        <span class="mark">TL</span>
        <div>
          <h1>TraceLink</h1>
          <p>企业执行追踪与工单管理</p>
        </div>
      </div>

      <div v-if="error" class="form-error">{{ error }}</div>

      <div class="field">
        <label for="username">账号</label>
        <input id="username" v-model="form.username" autocomplete="username" placeholder="登录账号" />
      </div>
      <div class="field">
        <label for="password">口令</label>
        <input id="password" v-model="form.password" type="password" autocomplete="current-password" placeholder="登录口令" />
      </div>

      <button class="btn btn-primary block" type="submit" :disabled="loading">
        {{ loading ? '登录中…' : '登录' }}
      </button>

      <p class="hint">演示账号 admin，口令 admin123。试点上线前请在账号设置里改掉。</p>
    </form>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { authApi } from '../api/org.js'
import { TOKEN_KEY, USER_KEY } from '../api/http.js'

const router = useRouter()
const route = useRoute()

const form = reactive({ username: '', password: '' })
const loading = ref(false)
const error = ref('')

async function submit() {
  if (loading.value) return
  error.value = ''
  if (!form.username.trim() || !form.password.trim()) {
    error.value = '请填写账号与口令'
    return
  }
  loading.value = true
  try {
    const result = await authApi.login(form.username.trim(), form.password)
    localStorage.setItem(TOKEN_KEY, result.token)
    localStorage.setItem(USER_KEY, JSON.stringify(result))
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/depts'
    router.replace(redirect)
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  background: radial-gradient(1200px 600px at 20% 0%, #FFFFFF 0%, var(--canvas) 60%);
}

.login-card {
  width: min(380px, 100%);
  padding: 28px 28px 22px;
}

.head {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 22px;
}

.mark {
  width: 38px;
  height: 38px;
  border-radius: var(--radius-sm);
  background: var(--txt);
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 14px;
}

h1 {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
}

.head p {
  margin: 2px 0 0;
  color: var(--txt-3);
  font-size: 12px;
}

.block {
  width: 100%;
  height: 38px;
  margin-top: 4px;
}

.hint {
  margin: 14px 0 0;
  color: var(--txt-3);
  font-size: 12px;
  line-height: 1.6;
}
</style>
