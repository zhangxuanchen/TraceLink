<template>
  <div class="heatmap">
    <div class="grid" :style="{ gridTemplateColumns: `80px repeat(${columns.length}, 1fr)` }">
      <div class="corner"></div>
      <div v-for="col in columns" :key="`c-${col}`" class="col-label">{{ col }}</div>
      <template v-for="row in rows" :key="`r-${row}`">
        <div class="row-label" :title="row">{{ row }}</div>
        <div v-for="col in columns" :key="`${row}-${col}`" class="cell"
             :style="{ background: color(row, col) }"
             :title="`${row} · ${col}：${value(row, col)}`"></div>
      </template>
    </div>
    <div class="legend">
      <span>密度</span>
      <i v-for="step in scale" :key="step" :style="{ background: step }"></i>
      <span>高</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  cells: { type: Array, default: () => [] }
})

const scale = ['#F4F6F8', '#DCE8FF', '#A9C8FF', '#6E9EFF', '#2E6FD9']

const rows = computed(() => [...new Set(props.cells.map((c) => c.row))])
const columns = computed(() => [...new Set(props.cells.map((c) => c.column))])

const max = computed(() => Math.max(...props.cells.map((c) => Number(c.value) || 0), 1))

function find(row, col) {
  return props.cells.find((c) => c.row === row && c.column === col)
}

function value(row, col) {
  return Number(find(row, col)?.value) || 0
}

function color(row, col) {
  const v = value(row, col)
  if (v === 0) return scale[0]
  const ratio = v / max.value
  const index = Math.min(scale.length - 1, 1 + Math.floor(ratio * (scale.length - 2)))
  return scale[index]
}
</script>

<style scoped>
.grid {
  display: grid;
  gap: 3px;
  align-items: center;
}

.corner {
  height: 20px;
}

.col-label {
  font-size: 11px;
  color: var(--txt-3);
  text-align: center;
}

.row-label {
  font-size: 12px;
  color: var(--txt-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cell {
  height: 22px;
  border-radius: 3px;
}

.legend {
  display: flex;
  align-items: center;
  gap: 4px;
  justify-content: flex-end;
  font-size: 11px;
  color: var(--txt-3);
  padding-top: 8px;
}

.legend i {
  width: 16px;
  height: 8px;
  border-radius: 2px;
}
</style>
