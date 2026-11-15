import { onMounted, watch, nextTick } from 'vue'

/**
 * 高亮定位：从工作台敏感项点击跳转到某个列表页后，滚动到目标行并闪烁高亮。
 * 用法：在目标列表页调用 useFlashLocate(selectorFn, { refer, id, getRecId })
 *   selectorFn(recId)   返回该条记录对应的 DOM 元素（找不到返回 null）
 *   options.refer       来源类型，如 FEEDBACK / MERCHANT_MESSAGE / REFUND
 *   options.id          目标业务 ID（来自 query）
 *   options.getRecId    从记录对象取 ID
 *   options.onFound     定位到后的回调（可选）
 */
export function useFlashLocate(route, selectorFn, { refer, id, getRecId, onFound, deps } = {}) {
  async function locate() {
    const targetId = id && refer ? Number(id) : null
    if (!targetId || !refer) return
    // 等列表渲染完成
    await nextTick()
    await nextTick()
    // 列表可能分页，优先直接找；找不到则尝试翻页由父级处理
    let el = null
    try {
      el = findRow(targetId)
    } catch {
      el = null
    }
    if (!el) {
      if (typeof onFound === 'function') onFound(targetId, false)
      return
    }
    flashRow(el)
    if (typeof onFound === 'function') onFound(targetId, true)
  }

  function findRow(targetId) {
    if (typeof selectorFn === 'function') return selectorFn(targetId)
    if (typeof getRecId !== 'function') return null
    // 兜底：按 data-recid 查找
    return document.querySelector(`[data-recid="${targetId}"]`)
  }

  function flashRow(el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'center' })
    el.classList.add('flash-target')
    // 闪烁 3 次后移除
    setTimeout(() => el.classList.remove('flash-target'), 2400)
  }

  onMounted(locate)
  watch(() => route.query.id, locate)

  return { locate }
}