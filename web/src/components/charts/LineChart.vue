<template>
  <div class="chart">
    <svg :viewBox="`0 0 ${width} ${height}`" preserveAspectRatio="none" role="img">
      <g class="grid">
        <line v-for="t in yTicks" :key="`g-${t}`" :x1="pad.left" :x2="width - pad.right" :y1="y(t)" :y2="y(t)" />
      </g>
      <text v-for="t in yTicks" :key="`yt-${t}`" :x="pad.left - 6" :y="y(t) + 4" class="axis" text-anchor="end">
        {{ shortNumber(t) }}
      </text>
      <path v-for="s in paths" :key="s.label" :d="s.d" class="line" :stroke="s.color" fill="none" />
      <circle v-for="p in dots" :key="p.key" :cx="p.x" :cy="p.y" r="2.4" :fill="p.color" />
      <text v-for="l in xLabels" :key="l.key" :x="l.x" :y="height - pad.bottom + 16" class="axis" text-anchor="middle">
        {{ l.text }}
      </text>
    </svg>
    <div class="legend">
      <span v-for="s in series" :key="s.label">
        <i :style="{ background: s.color || 'var(--info)' }"></i>{{ s.label }}
      </span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  labels: { type: Array, default: () => [] },
  series: { type: Array, default: () => [] },
  height: { type: Number, default: 240 },
  width: { type: Number, default: 720 },
  yMin: { type: Number, default: 0 }
})

const PALETTE = ['var(--info)', 'var(--ok)', 'var(--warn)', 'var(--accent)', 'var(--bad)']
const pad = { top: 12, right: 12, bottom: 26, left: 36 }

const bound = computed(() => {
  const values = props.series.flatMap((s) => (s.values || []).map((v) => Number(v) || 0))
  const max = values.length ? Math.max(...values) : 1
  return { min: props.yMin, max: max === 0 ? 1 : max * 1.1 }
})

const yTicks = computed(() => {
  const { min, max } = bound.value
  const step = (max - min) / 4
  return [0, 1, 2, 3, 4].map((i) => min + step * i)
})

const y = (value) => {
  const { min, max } = bound.value
  const ratio = (value - min) / (max - min || 1)
  return pad.top + (1 - ratio) * (props.height - pad.top - pad.bottom)
}

const x = (index) => {
  const count = Math.max(props.labels.length - 1, 1)
  const span = props.width - pad.left - pad.right
  return pad.left + (span * index) / count
}

const paths = computed(() =>
  props.series.map((s, si) => ({
    label: s.label,
    color: s.color || PALETTE[si % PALETTE.length],
    d: (s.values || [])
      .map((v, i) => `${i === 0 ? 'M' : 'L'}${x(i)},${y(Number(v) || 0)}`)
      .join(' ')
  }))
)

const dots = computed(() => {
  const list = []
  props.series.forEach((s, si) => {
    const color = s.color || PALETTE[si % PALETTE.length]
    ;(s.values || []).forEach((v, i) => {
      list.push({ key: `${si}-${i}`, x: x(i), y: y(Number(v) || 0), color })
    })
  })
  return list
})

const xLabels = computed(() => {
  const count = props.labels.length
  const step = count > 8 ? Math.ceil(count / 8) : 1
  return props.labels
    .map((text, i) => ({ key: i, x: x(i), text, i }))
    .filter((item) => item.i % step === 0 || item.i === count - 1)
})

function shortNumber(value) {
  const n = Number(value) || 0
  if (n >= 10000) return `${Math.round(n / 1000)}k`
  return Number.isInteger(n) ? String(n) : n.toFixed(1)
}
</script>

<style scoped>
.chart {
  width: 100%;
}

svg {
  width: 100%;
  display: block;
}

.grid line {
  stroke: var(--divider);
}

.axis {
  fill: var(--txt-3);
  font-size: 10px;
}

.line {
  stroke-width: 1.8;
  stroke-linecap: round;
}

.legend {
  display: flex;
  gap: 14px;
  justify-content: flex-end;
  font-size: 12px;
  color: var(--txt-2);
  padding-top: 4px;
}

.legend i {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 4px;
  margin-right: 5px;
}
</style>
