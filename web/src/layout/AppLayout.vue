<template>
  <div class="shell">
    <aside class="side">
      <div class="brand">
        <span class="mark">TL</span>
        <div>
          <div class="brand-name">TraceLink</div>
          <div class="brand-sub">执行追踪与工单</div>
        </div>
      </div>
      <nav class="nav">
        <template v-for="group in visibleGroups" :key="group.label">
          <div v-if="group.label" class="nav-group">{{ group.label }}</div>
          <router-link v-for="item in group.items" :key="item.name" :to="{ name: item.name }" class="nav-item"
                       :class="{ 'nav-item-off': item.phase }">
            <span class="nav-text">{{ item.title }}</span>
            <span v-if="item.phase" class="nav-phase">{{ item.phase }}</span>
          </router-link>
        </template>
      </nav>
    </aside>
    <div class="main">
      <header class="top">
        <div class="crumb">{{ route.meta.title || '工单中心' }}</div>
        <div class="who">
          <span class="role">{{ roleLabel }}</span>
          <span class="name">{{ user.name || user.username }}</span>
          <button class="btn btn-link" @click="logout">退出</button>
        </div>
      </header>
      <main class="body">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { TOKEN_KEY, USER_KEY } from '../api/http.js'

const route = useRoute()
const router = useRouter()

/** 侧栏按 DESIGN 9.2 的页面地图分三组；roles 控制可见性，执行者看不到设置 */
const menuGroups = [
  {
    label: '工单',
    items: [
      { name: 'tasks', title: '工单中心' },
      { name: 'chat', title: '工单助手' },
      { name: 'stats', title: '统计看板' },
      { name: 'kb', title: '知识库' }
    ]
  },
  {
    label: '组织',
    items: [
      { name: 'depts', title: '部门管理' },
      { name: 'positions', title: '职位管理' },
      { name: 'employees', title: '人员管理' }
    ]
  },
  {
    label: '系统设置',
    items: [
      { name: 'model', title: '模型配置', roles: ['SYS_ADMIN', 'MANAGER'] }
    ]
  }
]

const user = computed(() => {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || '{}')
  } catch {
    return {}
  }
})

/** 评价只对管理者开放；负责人只能看建议队列，执行者不出现复盘入口 */
const visibleGroups = computed(() => {
  const roles = user.value.roles || []
  return menuGroups
    .map((group) => ({
      label: group.label,
      items: group.items.filter((item) => !item.roles || item.roles.some((role) => roles.includes(role)))
    }))
    .filter((group) => group.items.length)
})

const roleLabel = computed(() => {
  const map = { SYS_ADMIN: '系统管理员', MANAGER: '管理者', OWNER: '负责人', EXECUTOR: '执行者' }
  const roles = user.value.roles || []
  return roles.map((r) => map[r] || r).join(' · ') || '未分配角色'
})

function logout() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
  router.replace({ name: 'login' })
}
</script>

<style scoped>
.shell {
  display: flex;
  min-height: 100%;
}

.side {
  width: var(--sidebar-w);
  flex: 0 0 var(--sidebar-w);
  border-right: 1px solid var(--divider);
  background: var(--surface);
  display: flex;
  flex-direction: column;
  position: sticky;
  top: 0;
  height: 100vh;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 18px;
  border-bottom: 1px solid var(--divider);
}

.mark {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  background: var(--txt);
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 13px;
  letter-spacing: 0.5px;
}

.brand-name {
  font-weight: 600;
  font-size: 14px;
}

.brand-sub {
  color: var(--txt-3);
  font-size: 12px;
}

.nav {
  padding: 10px 8px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  overflow-y: auto;
}

.nav-group {
  padding: 10px 10px 4px;
  color: var(--txt-3);
  font-size: 11px;
  letter-spacing: 0.5px;
}

.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  color: var(--txt-2);
  text-decoration: none;
  font-size: 13px;
}

.nav-item:hover {
  background: #F6F7F8;
}

.router-link-active {
  color: var(--txt);
  background: #F2F3F5;
  font-weight: 500;
}

.router-link-active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 6px;
  bottom: 6px;
  width: 3px;
  border-radius: 2px;
  background: var(--txt);
}

.nav-phase {
  font-size: 11px;
  color: var(--txt-3);
  border: 1px solid var(--divider);
  border-radius: 8px;
  padding: 0 5px;
}

.main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.top {
  height: var(--toolbar-h);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  border-bottom: 1px solid var(--divider);
  background: var(--surface);
  position: sticky;
  top: 0;
  z-index: 10;
}

.crumb {
  font-size: 14px;
  font-weight: 500;
}

.who {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: var(--txt-2);
}

.role {
  color: var(--txt-3);
}

.body {
  padding: 24px;
}

@media (max-width: 860px) {
  .shell {
    flex-direction: column;
  }

  .side {
    width: 100%;
    flex: none;
    height: auto;
    position: static;
    border-right: none;
    border-bottom: 1px solid var(--divider);
  }

  .brand {
    display: none;
  }

  .nav {
    flex-direction: row;
    overflow-x: auto;
    padding: 8px;
  }

  .nav-group {
    display: none;
  }

  .nav-item {
    white-space: nowrap;
  }

  .router-link-active::before {
    left: 8px;
    right: 8px;
    top: auto;
    bottom: 0;
    width: auto;
    height: 3px;
  }

  .body {
    padding: 16px;
  }
}
</style>
