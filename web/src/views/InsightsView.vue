<template>
  <section class="insight-page">
    <div class="page-head">
      <div>
        <div class="title-row">
          <h2 class="page-title">重点发现</h2>
          <div v-if="visibleTabs.length > 1" class="insight-switch" role="tablist" aria-label="重点发现视图">
            <button v-for="item in visibleTabs" :key="item.key" class="insight-tab" role="tab"
                    :aria-selected="tab === item.key" :class="{ active: tab === item.key }"
                    @click="switchTab(item.key)">{{ item.label }}</button>
          </div>
        </div>
        <p class="page-sub">缺口矩阵看积压，复盘建议看结论：先说清哪件事卡在哪一步，再说谁在拖、下一步该谁补</p>
      </div>
    </div>

    <div v-if="tab === 'map'" class="insight-body" role="tabpanel" aria-label="完成地图">
      <CompletionMapView />
    </div>
    <div v-else class="insight-body" role="tabpanel" aria-label="复盘与建议">
      <ReviewView />
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { USER_KEY } from '../api/http.js'
import CompletionMapView from './CompletionMapView.vue'
import ReviewView from './ReviewView.vue'

/** 完成地图与复盘建议合到一页：两处读同一批工单数据，只差看法 */
const TABS = [
  { key: 'map', label: '完成地图' },
  { key: 'review', label: '复盘与建议' }
]

const route = useRoute()
const router = useRouter()

/** 复盘建议沿用原来的可见范围；执行者只从工单详情深链进来看事项视图与我的提醒 */
const canReview = computed(() => {
  try {
    const roles = JSON.parse(localStorage.getItem(USER_KEY) || '{}').roles || []
    return roles.includes('SYS_ADMIN') || roles.includes('MANAGER') || roles.includes('OWNER')
  } catch {
    return false
  }
})

const visibleTabs = computed(() => TABS.filter((item) => item.key !== 'review' || canReview.value))

const tab = ref(route.query.tab === 'review' && canReview.value ? 'review' : 'map')

function switchTab(next) {
  const target = next === 'review' && canReview.value ? 'review' : 'map'
  if (target === tab.value) return
  tab.value = target
  const query = { ...route.query }
  // 切到复盘时清掉事项链的 taskId，避免它带到另一块视图
  if (target === 'review') delete query.taskId
  query.tab = target
  router.replace({ query })
}

onMounted(() => {
  if (route.query.tab !== 'review') return
  if (canReview.value) tab.value = 'review'
  else {
    // 执行者没有复盘视图：把 tab 从地址里去掉，留在完成地图
    tab.value = 'map'
    const query = { ...route.query }
    delete query.tab
    router.replace({ query })
  }
})

watch(() => route.query.tab, (next) => {
  const target = next === 'review' && canReview.value ? 'review' : 'map'
  if (target !== tab.value) tab.value = target
  // 无权看复盘的账号即便在页内改地址，也把 tab 从 query 里去掉
  if (next === 'review' && !canReview.value) {
    const query = { ...route.query }
    delete query.tab
    router.replace({ query })
  }
})
</script>

<style scoped>
.insight-page {
  position: relative;
}

.insight-page :deep(.page-head) {
  margin-bottom: 16px;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.insight-switch {
  display: inline-flex;
  border: 1px solid var(--divider);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.insight-tab {
  border: none;
  background: var(--surface);
  padding: 6px 14px;
  font-size: 13px;
  color: var(--txt-2);
  cursor: pointer;
}

.insight-tab + .insight-tab {
  border-left: 1px solid var(--divider);
}

.insight-tab.active {
  background: var(--txt);
  color: #fff;
}

.insight-tab:focus-visible {
  outline: 2px solid var(--border-strong, var(--txt));
  outline-offset: -2px;
}

/* 两块视图各自带页头，合并后把它们的动作按钮提到本页标题那一行的右侧 */
.insight-body :deep(.page-head) {
  position: absolute;
  top: 0;
  right: 0;
  margin: 0;
  min-height: 34px;
  align-items: center;
  justify-content: flex-end;
  pointer-events: none;
}

.insight-body :deep(.page-head > div:first-child) {
  display: none;
}

.insight-body :deep(.head-actions) {
  pointer-events: auto;
}

@media (max-width: 860px) {
  .insight-body :deep(.page-head) {
    position: static;
    margin: -6px 0 12px;
    pointer-events: auto;
  }
}
</style>
