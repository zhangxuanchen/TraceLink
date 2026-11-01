<template>
  <div class="radar">
    <svg viewBox="0 0 240 240" role="img">
      <g class="rings">
        <polygon v-for="ring in rings" :key="ring" :points="polygon(ring)" />
      </g>
      <line v-for="(axis, i) in axes" :key="`a-${i}`" x1="120" y1="120" :x2="axis.x" :y2="axis.y" class="spoke" />
      <polygon :points="polygon(1, values)" class="area" />
      <circle v-for="(point, i) in valuePoints" :key="`p-${i}`" :cx="point.x" :cy="point.y" r="3" class="dot" />
      <text v-for="axis in axes" :key="`t-${axis.label}`" :x="axis.textX" :y="axis.textY" class="axis"
            :text-anchor="axis.anchor">{{ axis.label }}</text>
    </svg>
    <div class="values">
      <div v-for="axis in axes" :key="`v-${axis.label}`" class="value">
        <span>{{ axis.label }}</span>
        <span class="num">{{ axis.value }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  dims: { type: Array, default: () => [] }
})

const center = 120
const radius = 82
const minValue = 0
const maxValue = 100

const angles = computed(() => {
  const count = Math.max(props.dims.length, 3)
  return props.dims.map((_, i) => (Math.PI * 2 * i) / count - Math.PI / 2)
})

const values = computed(() => props.dims.map((d) => Math.min(maxValue, Math.max(minValue, Number(d.value) || 0))))

function pointAt(angle, ratio) {
  return { x: center + Math.cos(angle) * radius * ratio, y: center + Math.sin(angle) * radius * ratio }
}

const polygon = (ratio, series = null) => {
  const list = angles.value.map((angle, i) => {
    const r = series ? (series[i] / maxValue) * ratio : ratio
    const p = pointAt(angle, r)
    return `${p.x},${p.y}`
  })
  return list.join(' ') || '120,120'
}

const rings = [0.25, 0.5, 0.75, 1]

const axes = computed(() =>
  angles.value.map((angle, i) => {
    const end = pointAt(angle, 1)
    const labelSpot = pointAt(angle, 1.16)
    const anchor = Math.abs(labelSpot.x - center) < 6 ? 'middle' : labelSpot.x > center ? 'start' : 'end'
    return {
      label: props.dims[i]?.label || '',
      value: Math.round(values.value[i]),
      x: end.x,
      y: end.y,
      textX: labelSpot.x,
      textY: labelSpot.y + 3,
      anchor
    }
  })
)

const valuePoints = computed(() =>
  angles.value.map((angle, i) => pointAt(angle, values.value[i] / maxValue))
)
</script>

<style scoped>
.radar {
  display: flex;
  align-items: center;
  gap: 16px;
}

svg {
  width: 240px;
  height: 240px;
  flex: none;
}

.rings polygon {
  fill: none;
  stroke: var(--divider);
}

.spoke {
  stroke: var(--divider);
}

.area {
  fill: rgba(10, 108, 255, 0.12);
  stroke: var(--info);
  stroke-width: 1.6;
}

.dot {
  fill: var(--info);
}

.axis {
  fill: var(--txt-2);
  font-size: 11px;
}

.values {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: var(--txt-2);
  min-width: 0;
}

.value {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.value .num {
  color: var(--txt);
}
</style>
