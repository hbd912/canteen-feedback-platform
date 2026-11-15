<template>
  <div ref="chartRef" :style="{ width: '100%', height: height }"></div>
</template>

<script setup>
import { ref, shallowRef, onMounted, onBeforeUnmount, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  option: { type: Object, required: true },
  height: { type: String, default: '360px' }
})

const emit = defineEmits(['click'])

const chartRef = ref(null)
const instance = shallowRef(null)
let resizeObserver = null
let initRetry = null

function render() {
  if (!instance.value) return
  instance.value.setOption(props.option, true)
}

function handleResize() {
  // 仅当容器宽度不为 0 时 resize，避免 keep-alive 隐藏/未布局期间把图缩到 0
  if (chartRef.value && chartRef.value.clientWidth > 0) {
    instance.value && instance.value.resize()
  }
}

/** 修复图5错位：容器宽度不再直接等于窗口宽度（折叠侧栏/断点/激活页面都会变），
 *  用 ResizeObserver 监听容器真实尺寸，任何导致宽度变化的情况都能纠正 echarts 布局。 */
let initTries = 0
function initChart() {
  if (!chartRef.value) return
  const w = chartRef.value.clientWidth
  if (w <= 0) {
    // 容器还没布局（keep-alive 切走再回来）：等下一帧再试，避免 echarts 以 0 宽初始化。
    // 最多试 50 次（3 秒），若仍为 0 就放弃：ResizeObserver 会在容器真正有宽时兜底初始化。
    if (++initTries <= 50) {
      initRetry = setTimeout(initChart, 60)
    } else {
      initTries = 0
    }
    return
  }
  if (instance.value) return
  instance.value = echarts.init(chartRef.value)
  // 点击图形时把事件透出给父组件（setOption 不会移除该监听）
  instance.value.on('click', params => emit('click', params))
  render()
}

onMounted(() => {
  initChart()
  window.addEventListener('resize', handleResize)
  // 关键修复：容器尺寸变化（非窗口）也触发 resize
  if (typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(() => {
      if (instance.value) {
        handleResize()
      } else if (chartRef.value && chartRef.value.clientWidth > 0) {
        initChart()
      }
    })
    resizeObserver.observe(chartRef.value)
  }
})

watch(() => props.option, render, { deep: true })
// 高度从固定像素切成 100% 时容器尺寸会变，补一次 resize 防止图表按旧高度绘制
watch(() => props.height, () => requestAnimationFrame(handleResize))

// 允许父组件导出 PNG（销售分析里的「下载图表」）
defineExpose({
  getDataURL: (opts = {}) => instance.value
    ? instance.value.getDataURL({ pixelRatio: 2, backgroundColor: '#ffffff', ...opts })
    : null
})

onBeforeUnmount(() => {
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
  if (initRetry) {
    clearTimeout(initRetry)
    initRetry = null
  }
  window.removeEventListener('resize', handleResize)
  instance.value && instance.value.dispose()
  instance.value = null
})
</script>
