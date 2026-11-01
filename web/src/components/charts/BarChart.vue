<template>
  <div class="bar-chart" :class="{ horizontal }">
    <div v-if="horizontal" class="rows">
      <div v-for="(item, i) in visible" :key="item.label + i" class="row">
        <span class="label" :title="item.label">{{ item.label }}</span>
        <div class="track">
          <div class="fill" :style="{ width: ratio(item.value), background: color(item, i) }"></div>
          <div v-if="target !== null" class="target" :style="{ left: ratio(target) }"></div>
        </div>
        <span class="value num">{{ format(item.value) }}</span>
      </div>
      <div v-if="!visible.length" class="empty">暂无数据</div>
    </div>
    <div v-else class="columns">
      <div v-for="(item, i) in visible" :key="item.label + i" class="column">
        <div class="stack">
          <span class="value num">{{ format(item.value) }}</span>
          <div class="fill" :style="{ height: ratio(item.value), background: color(item, i) }"></div>
        </div>
        <span class="label" :title="item.label">{{ item.label }}</span>
      </div>
      <div v-if="!visible.length" class="empty">暂无数据</div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  items: { type: Array, default: () => [] },
  horizontal: { type: Boolean, default: false },
  target: { type: Number, default: null },
  limit: { type: Number, default: 12 },
  palette: { type: Array, default: () => ['var(--info)', 'var(--ok)', 'var(--warn)', 'var(--accent)', 'var(--bad)'] }
})

const visible = computed(() => {
  const list = props.items || []
  return props.horizontal ? list.slice(0, props.limit) : list.slice(0, props.limit)
})

const max = computed(() => {
  const values = visible.value.map((i) => Number(i.value) || 0)
  const peak = Math.max(...values, props.target || 0, 1)
  return peak
})

const ratio = (value) => `${Math.min(100, ((Number(value) || 0) / max.value) * 100)}%`

function color(item, index) {
  return item.color || props.palette[index % props.palette.length]
}

function format(value) {
  const n = Number(value) || 0
  return Number.isInteger(n) ? n : n.toFixed(1)
}
</script>

<style scoped>
.rows .row {
  display: grid;
  grid-template-columns: 96px 1fr 48px;
  align-items: center;
  gap: 10px;
  padding: 4px 0;
}

.label {
  font-size: 12px;
  color: var(--txt-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.track {
  position: relative;
  height: 10px;
  background: #F1F2F4;
  border-radius: 5px;
}

.fill {
  height: 100%;
  border-radius: 5px;
  min-width: 2px;
  transition: width 200ms ease;
}

.target {
  position: absolute;
  top: -2px;
  width: 1px;
  height: 14px;
  background: var(--txt);
  opacity: 0.5;
}

.value {
  font-size: 12px;
  color: var(--txt);
  text-align: right;
}

.columns {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  height: 200px;
  padding-top: 12px;
}

.column {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100%;
}

.stack {
  flex: 1;
  width: 100%;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  align-items: center;
  gap: 4px;
}

.columns .fill {
  width: 60%;
  min-height: 2px;
  transition: height 200ms ease;
}

.columns .label {
  margin-top: 6px;
  max-width: 100%;
  font-size: 11px;
}

.empty {
  padding: 24px;
  color: var(--txt-3);
  font-size: 13px;
  text-align: center;
  flex: 1;
}
</style>
