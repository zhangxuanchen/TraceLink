<template>
  <div class="mask" @click.self="close">
    <div class="panel card">
      <header class="panel-head">
        <h3>{{ title }}</h3>
        <button class="btn-link" aria-label="关闭" @click="close">×</button>
      </header>
      <div class="panel-body">
        <slot />
      </div>
      <footer class="panel-foot">
        <slot name="footer" />
      </footer>
    </div>
  </div>
</template>

<script setup>
const props = defineProps({
  title: { type: String, default: '' }
})
const emit = defineEmits(['close'])

function close() {
  emit('close')
}
</script>

<style scoped>
.mask {
  position: fixed;
  inset: 0;
  background: rgba(16, 24, 40, 0.36);
  display: grid;
  place-items: center;
  z-index: 60;
  padding: 24px;
}

.panel {
  width: min(560px, 100%);
  max-height: 86vh;
  display: flex;
  flex-direction: column;
  box-shadow: var(--shadow-pop);
  background: var(--surface);
  border-radius: var(--radius-lg);
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--divider);
}

.panel-head h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.panel-head .btn-link {
  font-size: 20px;
  line-height: 1;
  color: var(--txt-3);
}

.panel-body {
  padding: 18px 20px 6px;
  overflow: auto;
}

.panel-foot {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 12px 20px 16px;
  border-top: 1px solid var(--divider);
}
</style>
