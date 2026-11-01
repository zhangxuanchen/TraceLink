<template>
  <div class="pair-bar">
    <div v-for="item in visible" :key="item.label" class="group">
      <div class="bars">
        <div class="bar planned" :style="{ height: ratio(item.planned) }" :title="`计划 ${item.planned}`"></div>
        <div class="bar actual" :style="{ height: ratio(item.actual) }" :title="`实际 ${item.actual}`"></div>
      </div>
      <div class="labels">
        <span class="label">{{ item.label }}</span>
        <span class="rate num" :class="rateClass(item)">{{ rate(item) }}%</span>
      </div>
    </div>
    <div v-if="!visible.length" class="empty">暂无生产指标</div>
    <div v-if="visible.length" class="legend">
      <span><i class="planned"></i>计划</span>
      <span><i class="actual"></i>实际</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  items: { type: Array, default: () => [] },
  limit: { type: Number, default: 12 }
})

const visible = computed(() => props.items.slice(0, props.limit))

const max = computed(() => {
  const values = visible.value.flatMap((i) => [Number(i.planned) || 0, Number(i.actual) || 0])
  return Math.max(...values, 1)
})

const ratio = (value) => `${Math.max(2, ((Number(value) || 0) / max.value) * 100)}%`

const rate = (item) => {
  const planned = Number(item.planned) || 0
  const actual = Number(item.actual) || 0
  if (!planned) return 0
  return Math.round((actual / planned) * 100)
}

function rateClass(item) {
  const value = rate(item)
  if (value >= 100) return 'ok'
  if (value >= 80) return 'warn'
  return 'bad'
}
</script>

<style scoped>
.pair-bar {
  display: flex;
  align-items: flex-end;
  gap: 14px;
  padding: 8px 0 0;
  min-height: 200px;
}

.group {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  height: 180px;
}

.bars {
  flex: 1;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: 3px;
}

.bar {
  width: 12px;
  min-height: 2px;
  border-radius: 3px 3px 0 0;
  transition: height 200ms ease;
}

.bar.planned,
.legend i.planned {
  background: #C9D6E4;
}

.bar.actual,
.legend i.actual {
  background: var(--info);
}

.labels {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding-top: 6px;
}

.label {
  font-size: 11px;
  color: var(--txt-2);
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rate {
  font-size: 11px;
}

.rate.ok { color: var(--ok); }
.rate.warn { color: var(--warn); }
.rate.bad { color: var(--bad); }

.legend {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: var(--txt-2);
  align-self: flex-start;
}

.legend i {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 4px;
  margin-right: 4px;
}

.empty {
  flex: 1;
  text-align: center;
  color: var(--txt-3);
  font-size: 13px;
  padding: 24px;
}
</style>
