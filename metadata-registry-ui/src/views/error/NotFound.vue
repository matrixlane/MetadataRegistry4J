<template>
  <div class="not-found">
    <el-result title="404" sub-title="页面不存在">
      <template #icon>
        <el-icon :size="80" color="#2563eb"><Warning /></el-icon>
      </template>
      <template #extra>
        <el-button type="primary" @click="$router.push('/dashboard')">返回首页</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup lang="ts"></script>

<style scoped>
.not-found {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}
</style>
