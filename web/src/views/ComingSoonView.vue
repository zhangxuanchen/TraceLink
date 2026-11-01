<template>
  <section>
    <div class="page-head">
      <div>
        <h2 class="page-title">{{ title }}</h2>
        <p class="page-sub">该模块排在 {{ phase }}，骨架期先留入口，点进来不会出现空白页</p>
      </div>
    </div>
    <div class="card empty">
      {{ title }} 正在按里程碑推进，当前可用的只有组织三件套与登录。
      <div class="back">
        <button class="btn" @click="goBack">回到部门管理</button>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const title = computed(() => route.meta.title || '建设中')
const phase = computed(() => route.meta.phase || '后续版本')

function goBack() {
  router.push({ name: 'depts' })
}
</script>

<style scoped>
.back {
  margin-top: 14px;
}
</style>
