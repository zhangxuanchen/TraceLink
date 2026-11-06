<template>
  <div ref="root" class="multi-select">
    <button type="button" class="ms-trigger" :class="{ open, active: modelValue.length > 0 }"
            @click="toggle" @keydown.esc="open = false">
      <span class="ms-text">{{ summary }}</span>
      <span class="ms-caret"></span>
    </button>
    <div v-if="open" class="ms-panel">
      <label v-for="opt in options" :key="opt.value" class="ms-option">
        <input type="checkbox" :checked="modelValue.includes(opt.value)" @change="pick(opt.value)" />
        <span>{{ opt.label }}</span>
      </label>
      <div v-if="!options.length" class="ms-empty">没有可选项</div>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

/** 下拉多选：勾选立即生效，点空白处收起；全不选等于不过滤 */
const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  options: { type: Array, default: () => [] },
  placeholder: { type: String, default: '全部' }
})
const emit = defineEmits(['update:modelValue', 'change'])

const root = ref(null)
const open = ref(false)

const summary = computed(() => {
  const picked = props.options.filter((opt) => props.modelValue.includes(opt.value))
  if (!picked.length) return props.placeholder
  if (picked.length === 1) return picked[0].label.trim()
  return `${picked[0].label.trim()} +${picked.length - 1}`
})

function toggle() {
  open.value = !open.value
}

function pick(value) {
  const next = props.modelValue.includes(value)
    ? props.modelValue.filter((item) => item !== value)
    : [...props.modelValue, value]
  emit('update:modelValue', next)
  emit('change', next)
}

function onOutside(event) {
  if (open.value && root.value && !root.value.contains(event.target)) {
    open.value = false
  }
}

onMounted(() => document.addEventListener('mousedown', onOutside))
onBeforeUnmount(() => document.removeEventListener('mousedown', onOutside))
</script>

<style scoped>
.multi-select {
  position: relative;
}

.ms-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  max-width: 220px;
  padding: 0 10px;
  color: var(--txt);
  font-size: 13px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  cursor: pointer;
}

.ms-trigger.active {
  border-color: var(--accent);
  color: var(--accent);
}

.ms-trigger.open {
  border-color: var(--accent);
}

.ms-text {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.ms-caret {
  flex: none;
  width: 10px;
  height: 6px;
  margin-left: auto;
  background: url("data:image/svg+xml;charset=utf-8,%3Csvg xmlns='http://www.w3.org/2000/svg' width='10' height='6' viewBox='0 0 10 6'%3E%3Cpath d='M1 1l4 4 4-4' fill='none' stroke='%239AA0A6' stroke-width='1.5' stroke-linecap='round' stroke-linejoin='round'/%3E%3C/svg%3E") no-repeat center;
}

.ms-panel {
  position: absolute;
  z-index: 30;
  top: calc(100% + 4px);
  left: 0;
  min-width: 100%;
  max-height: 280px;
  overflow-y: auto;
  padding: 6px;
  background: var(--surface);
  border: 1px solid var(--divider);
  border-radius: var(--radius-sm);
  box-shadow: var(--shadow-pop);
}

.ms-option {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: var(--radius-sm);
  font-size: 13px;
  white-space: nowrap;
  cursor: pointer;
}

.ms-option:hover {
  background: rgba(0, 0, 0, 0.04);
}

.ms-option input {
  height: auto;
  padding: 0;
}

.ms-empty {
  padding: 8px;
  color: var(--txt-3);
  font-size: 12px;
}
</style>
