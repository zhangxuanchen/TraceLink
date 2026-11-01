<template>
  <div class="donut">
    <svg viewBox="0 0 120 120" role="img">
      <circle cx="60" cy="60" r="46" class="base" />
      <circle v-for="arc in arcs" :key="arc.label" cx="60" cy="60" r="46" class="arc"
              :stroke="arc.color" :stroke-dasharray="`${arc.length} ${circumference}`"
              :stroke-dashoffset="arc.offset" />
      <text x="60" y="56" class="center-value">{{ total }}</text>
      <text x="60" y="72" class="center-label">合计</text>
    </svg>
    <div class="legend">
      <div v-for="arc in arcs" :key="`l-${arc.label}`" class="legend-item">
        <i :style="{ background: arc.color }"></i>
        <span class="label">{{ arc.label }}</span>
        <span class="value num">{{ arc.value }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  items: { type: Array, default: () => [] },
  palette: { type: Array, default: () => ['var(--info)', 'var(--ok)', 'var(--warn)', 'var(--accent)', 'var(--bad)', '#455A64'] }
})

const radius = 46
const circumference = 2 * Math.PI * radius

const total = computed(() => props.items.reduce((sum, i) => sum + (Number(i.value) || 0), 0))

const arcs = computed(() => {
  let offset = 0
  return props.items.map((item, index) => {
    const value = Number(item.value) || 0
    const length = total.value === 0 ? 0 : (value / total.value) * circumference
    const arc = {
      label: item.label,
      value,
      color: item.color || props.palette[index % props.palette.length],
      length,
      offset: -offset
    }
    offset += length
    return arc
  })
})
</script>

<style scoped>
.donut {
  display: flex;
  align-items: center;
  gap: 20px;
}

svg {
  width: 140px;
  height: 140px;
  flex: none;
}

.base {
  fill: none;
  stroke: #F1F2F4;
  stroke-width: 14;
}

.arc {
  fill: none;
  stroke-width: 14;
  stroke-linecap: butt;
  transform: rotate(-90deg);
  transform-origin: 60px 60px;
}

.center-value {
  text-anchor: middle;
  font-size: 18px;
  font-weight: 600;
  fill: var(--txt);
}

.center-label {
  text-anchor: middle;
  font-size: 9px;
  fill: var(--txt-3);
}

.legend {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.legend-item {
  display: grid;
  grid-template-columns: 8px 1fr auto;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.legend-item i {
  width: 8px;
  height: 8px;
  border-radius: 4px;
}

.label {
  color: var(--txt-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.value {
  color: var(--txt);
}
</style>
