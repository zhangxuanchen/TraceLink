import { createRouter, createWebHashHistory } from 'vue-router'
import { TOKEN_KEY, USER_KEY } from '../api/http.js'

/** 登录后落地：驾驶舱与我的工作台都已下线，统一进工单中心 */
export function landingRoute() {
  return { name: 'tasks' }
}

const routes = [
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('../layout/AppLayout.vue'),
    redirect: () => landingRoute(),
    children: [
      // 驾驶舱与我的工作台已下线，旧链接回到工单中心
      { path: 'dashboard', redirect: () => landingRoute() },
      { path: 'desk', redirect: (to) => ({ name: 'tasks', query: { ...to.query } }) },
      { path: 'depts', name: 'depts', component: () => import('../views/DeptView.vue'), meta: { title: '部门管理' } },
      { path: 'positions', name: 'positions', component: () => import('../views/PositionView.vue'), meta: { title: '职位管理' } },
      { path: 'employees', name: 'employees', component: () => import('../views/EmployeeView.vue'), meta: { title: '人员管理' } },
      { path: 'tasks', name: 'tasks', component: () => import('../views/TaskView.vue'), meta: { title: '工单中心' } },
      // 看板视图已下线，旧链接回到工单中心列表
      { path: 'tasks/board', name: 'board', redirect: (to) => ({ name: 'tasks', query: { ...to.query } }) },
      { path: 'tasks/:id', name: 'task-detail', component: () => import('../views/TaskDetailView.vue'), meta: { title: '工单详情' } },
      // 日报是工单的集合，不再单开一页：旧链接回到工单中心，导出在页底的「导出工单与日报」里
      { path: 'reports', name: 'reports', redirect: (to) => ({ name: 'tasks', query: { ...to.query } }) },
      { path: 'stats', name: 'stats', component: () => import('../views/StatsView.vue'), meta: { title: '统计看板' } },
      // 完成地图与复盘建议已并成一页「重点发现」，旧链接按 tab 还原；执行者只能从工单详情深链进来
      { path: 'insights', name: 'insights', component: () => import('../views/InsightsView.vue'),
        meta: { title: '重点发现' } },
      { path: 'map', name: 'map', redirect: (to) => ({ name: 'insights', query: { ...to.query, tab: 'map' } }) },
      { path: 'stats/review', name: 'review', redirect: (to) => ({ name: 'insights', query: { ...to.query, tab: 'review' } }) },
      // 导入向导不在侧栏，工单中心也不再放入口；`#/import` 仍可深链进入，需要新的入口时再挂
      { path: 'import', name: 'import', component: () => import('../views/ImportView.vue'),
        meta: { title: '导入向导', roles: ['SYS_ADMIN', 'MANAGER', 'OWNER'] } },
      { path: 'kb', name: 'kb', component: () => import('../views/KnowledgeView.vue'), meta: { title: '知识库' } },
      { path: 'chat', name: 'chat', component: () => import('../views/ChatView.vue'), meta: { title: '工单助手' } },
      // 角色已并进人员管理，旧链接回到人员页
      { path: 'settings/roles', redirect: () => ({ name: 'employees' }) },
      // 模板与参数、审计日志不再单独成页，旧链接回到系统设置里唯一保留的模型配置
      { path: 'settings/templates', redirect: () => ({ name: 'model' }) },
      { path: 'settings/audit', redirect: () => ({ name: 'model' }) },
      { path: 'settings/model', name: 'model', component: () => import('../views/ModelConfigView.vue'),
        meta: { title: '模型配置', roles: ['SYS_ADMIN', 'MANAGER'] } }
    ]
  }
]

export const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to) => {
  if (!to.meta.public && !localStorage.getItem(TOKEN_KEY)) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && localStorage.getItem(TOKEN_KEY)) {
    return landingRoute()
  }
  const allowed = to.meta.roles
  if (allowed) {
    let roles = []
    try {
      roles = JSON.parse(localStorage.getItem(USER_KEY) || '{}').roles || []
    } catch {
      roles = []
    }
    if (!allowed.some((role) => roles.includes(role))) {
      return landingRoute()
    }
  }
  return true
})
