import { createRouter, createWebHashHistory } from 'vue-router'
import { TOKEN_KEY } from '../api/http.js'

const routes = [
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('../layout/AppLayout.vue'),
    redirect: '/depts',
    children: [
      { path: 'depts', name: 'depts', component: () => import('../views/DeptView.vue'), meta: { title: '部门管理' } },
      { path: 'positions', name: 'positions', component: () => import('../views/PositionView.vue'), meta: { title: '职位管理' } },
      { path: 'employees', name: 'employees', component: () => import('../views/EmployeeView.vue'), meta: { title: '人员管理' } },
      { path: 'tasks', name: 'tasks', component: () => import('../views/TaskView.vue'), meta: { title: '工单中心' } },
      { path: 'tasks/:id', name: 'task-detail', component: () => import('../views/TaskDetailView.vue'), meta: { title: '工单详情' } },
      { path: 'stats', name: 'stats', component: () => import('../views/ComingSoonView.vue'), meta: { title: '统计看板', phase: 'M2' } },
      { path: 'map', name: 'map', component: () => import('../views/ComingSoonView.vue'), meta: { title: '完成地图', phase: 'M3' } },
      { path: 'kb', name: 'kb', component: () => import('../views/ComingSoonView.vue'), meta: { title: '知识库', phase: 'M4' } },
      { path: 'chat', name: 'chat', component: () => import('../views/ComingSoonView.vue'), meta: { title: '会话助手', phase: 'M5' } }
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
    return { path: '/depts' }
  }
  return true
})
