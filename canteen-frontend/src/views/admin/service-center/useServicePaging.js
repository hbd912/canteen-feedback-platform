import { onBeforeUnmount } from 'vue'

// 与客服统一表格的 43px 表头、54px 单行一致；小窗口减少条数，分页始终留在底部。
export function useServicePaging(page, reload) {
  let observer
  let timer
  function bind(element) {
    if (!element) return
    observer?.disconnect()
    observer = new ResizeObserver(() => {
      clearTimeout(timer)
      timer = setTimeout(() => {
        if (element.clientHeight < 100) return
        const size = Math.min(60, Math.max(3, Math.floor((element.clientHeight - 44) / 54)))
        if (page.size === size) return
        page.size = size
        page.current = 1
        reload()
      }, 120)
    })
    observer.observe(element)
  }
  onBeforeUnmount(() => { observer?.disconnect(); clearTimeout(timer) })
  return { bind }
}
