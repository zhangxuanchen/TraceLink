<template>
  <div class="scatter">
    <svg :viewBox="`0 0 ${width} ${height}`" role="img">
      <line :x1="pad.left" :x2="width - pad.right" :y1="height - pad.bottom" :y2="height - pad.bottom" class="axis-line" />
      <line :x1="pad.left" :x2="pad.left" :y1="pad.top" :y2="height - pad.bottom" class="axis-line" />
      <text v-for="t in yTicks" :key="`y-${t}`" :x="pad.left - 6" :y="yScale(t) + 4" class="axis" text-anchor="end">{{ t }}</text>
      <text v-for="t in xTicks" :key="`x-${t}`" :x="xScale(t)" :y="height - pad.bottom + 16" class="axis" text-anchor="middle">{{ t }}</text>
      <text :x="width / 2" :y="height - 2" class="axis-title" text-anchor="middle">{{ xLabel }}</text>
      <text :x="12" :y="pad.top + 4" class="axis-title">{{ yLabel }}</text>
      <g v-for="p in points" :key="p.key">
        <circle :cx="p.x" :cy="p.y" :r="p.r" :fill="p.color" :opacity="p.opacity" />
      </g>
    </svg>
    <div class="hint">点的大小表示完成单数，颜色越深表示按时完成率越高</div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  points: { type: Array, default: () => [] },
  xLabel: { type: String, default: '产出' },
  yLabel: { type: String, default: '逾期' },
  width: { type: Number, default: 640 },
  height: { type: Number, default: 260 }
})

const pad = { top: 24, right: 16, bottom: 30, left: 40 }

const bounds = computed(() => {
  const xs = props.points.map((p) => Number(p.x) || 0)
  const ys = props.points.map((p) => Number(p.y) || 0)
  const maxX = Math.max(...xs, 1) * 1.1
  const maxY = Math.max(...ys, 1) * 1.1
  return { maxX, maxY }
})

const xScale = (value) => pad.left + ((Number(value) || 0) / bounds.value.maxX) * (props.width - pad.left - pad.right)
const yScale = (value) => props.height - pad.bottom - ((Number(value) || 0) / bounds.value.maxY) * (props.height - pad.top - pad.bottom)

const yTicks = computed(() => [0, 1, 2, 3].map((i) => Math.round((bounds.value.maxY / 3) * i)))
const xTicks = computed(() => [0, 1, 2, 3].map((i) => Math.round((bounds.value.maxX / 3) * i)))

const points = computed(() =>
  props.points.map((p, i) => {
    const rate = Number(p.rate) || 0
    const done = Number(p.x) || 0
    return {
      key: i,
      x: xScale(done),
      y: yScale(p.y),
      r: 4 + Math.min(8, Math.sqrt(Number(p.size) || 1)),
      color: rate >= 90 ? 'var(--ok)' : rate >= 70 ? 'var(--info)' : 'var(--warn)',
      opacity: p.label ? 0.85 : 0.55
    }
  })
)
</script>

<style scoped>
svg {
  width: 100%;
  display: block;
}

.axis-line {
  stroke: var(--border);
}

.axis {
  fill: var(--txt-3);
  font-size: 10px;
}

.axis-title {
  fill: var(--txt-3);
  font-size: 10px;
}

.hint {
  font-size: 12px;
  color: var(--txt-3);
  text-align: right;
}
</style>
