<template>
  <div class="funnel">
    <div v-for="(step, i) in steps" :key="step.label" class="step">
      <div class="shape" :style="{ width: `${width(i)}%`, background: color(i) }">
        <span class="value num">{{ step.value }}</span>
      </div>
      <div class="meta">
        <span class="label">{{ step.label }}</span>
        <span class="rate num" v-if="i > 0">{{ rate(i) }}%</span>
      </div>
    </div>
    <div v-if="!steps.length" class="empty">暂无数据</div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  steps: { type: Array, default: () => [] },
  palette: { type: Array, default: () => ['var(--info)', '#3F82FF', '#5E97FF', '#7FADFF', '#A6C4FF'] }
})

const max = computed(() => Math.max(...props.steps.map((s) => Number(s.value) || 0), 1))

const width = (index) => {
  const base = (Number(props.steps[index]?.value) || 0) / max.value
  // 保证每一级都比上一级窄，视觉上形成漏斗
  return Math.max(18, base * 100 - index * 2)
}

const rate = (index) => {
  const prev = Number(props.steps[index - 1]?.value) || 0
  const current = Number(props.steps[index]?.value) || 0
  if (!prev) return '—'
  return Math.round((current / prev) * 100)
}

const color = (index) => props.palette[Math.min(index, props.palette.length - 1)]
</script>

<style scoped>
.funnel {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
}

.step {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
}

.shape {
  height: 26px;
  border-radius: 4px;
  display: grid;
  place-items: center;
  color: #fff;
  transition: width 200ms ease;
}

.value {
  font-size: 12px;
}

.meta {
  display: flex;
  gap: 8px;
  font-size: 12px;
  color: var(--txt-2);
}

.rate {
  color: var(--txt-3);
}

.empty {
  padding: 24px;
  color: var(--txt-3);
  font-size: 13px;
}
</style>
