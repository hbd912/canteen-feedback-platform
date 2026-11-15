import { defineStore } from 'pinia'

const KEY = 'canteen_dish_drafts'

function load() {
  try {
    const raw = localStorage.getItem(KEY)
    const list = raw ? JSON.parse(raw) : []
    return Array.isArray(list) ? list : []
  } catch {
    return []
  }
}

/**
 * AI 菜品草稿：先出现在「菜品管理 / 档口预览」里给商家看效果，
 * 商家满意后才真正 createDish；不满意重做会替换旧草稿。
 * 用 localStorage 持久化，刷新/切页面不会丢。
 */
export const useDishDraftStore = defineStore('dishDraft', {
  state: () => ({
    drafts: load()
  }),

  actions: {
    persist() {
      try {
        localStorage.setItem(KEY, JSON.stringify(this.drafts))
      } catch {
        // 忽略存储失败
      }
    },
    upsert(draft) {
      const idx = this.drafts.findIndex(d => d.localId === draft.localId)
      if (idx >= 0) {
        this.drafts[idx] = { ...this.drafts[idx], ...draft }
      } else {
        this.drafts.unshift(draft)
      }
      this.persist()
    },
    remove(localId) {
      this.drafts = this.drafts.filter(d => d.localId !== localId)
      this.persist()
    },
    clear() {
      this.drafts = []
      this.persist()
    }
  }
})
