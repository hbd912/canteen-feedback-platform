import request, { getToken } from './request'

// 会话
export function listSessions() {
  return request.get('/customer-service/sessions')
}

export function createSession(data) {
  return request.post('/customer-service/sessions', data)
}

export function getMessages(sessionId) {
  return request.get(`/customer-service/sessions/${sessionId}/messages`)
}

export function sendMessage(sessionId, content, dishId) {
  return request.post(`/customer-service/sessions/${sessionId}/messages`, { content, dishId }, { timeout: 35000 })
}

export function suggestAiReply(sessionId) {
  return request.post(`/customer-service/sessions/${sessionId}/ai-suggest`, null, { timeout: 35000 })
}

export function csUnreadCount() {
  return request.get('/customer-service/unread-count')
}

// 删除/隐藏会话（学生只隐藏自己的列表，商家只隐藏本档口的列表）
export function deleteSession(sessionId) {
  return request.delete(`/customer-service/sessions/${sessionId}`)
}

// 当前角色全部已读：清空学生/商家侧所有会话未读
export function readAllCsMessages() {
  return request.put('/customer-service/read-all')
}

// 单会话已读：聊天窗口开着时来新消息，立刻清掉该会话未读
export function readSessionMessages(sessionId) {
  return request.put(`/customer-service/sessions/${sessionId}/read`)
}

// 商家在会话里发一张可领取券（rate: 0.9=九折，0=免单；scope: ORDER整单/ITEM单份菜）
export function grantCouponToSession(sessionId, rate, scope = 'ORDER', dishId = null) {
  return request.post(`/customer-service/sessions/${sessionId}/grant-coupon`, { rate, scope, dishId })
}

/**
 * SSE 实时消息流。用 fetch 而不是 EventSource，是为了能带 Authorization 头。
 * 返回 { close }，切换会话/离开页面时调用。
 */
export function streamMessages(sessionId, { onMessage, onOpen, onError } = {}) {
  const controller = new AbortController()
  let closed = false

  const parseChunk = (chunk) => {
    let event = 'message'
    const dataLines = []
    for (const line of chunk.split('\n')) {
      if (line.startsWith('event:')) {
        event = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).trim())
      }
    }
    if (!dataLines.length) return
    if (event === 'connected') return
    if (event !== 'message') return
    try {
      onMessage && onMessage(JSON.parse(dataLines.join('\n')))
    } catch {
      // 忽略单条坏数据
    }
  }

  const run = async () => {
    try {
      const res = await fetch(`/api/customer-service/sessions/${sessionId}/stream`, {
        method: 'GET',
        headers: {
          Accept: 'text/event-stream',
          Authorization: `Bearer ${getToken()}`
        },
        signal: controller.signal
      })
      if (!res.ok || !res.body) {
        throw new Error(`SSE HTTP ${res.status}`)
      }
      onOpen && onOpen()
      const reader = res.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''
      while (!closed) {
        const { value, done } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        buffer = buffer.replace(/\r\n/g, '\n')
        let idx
        while ((idx = buffer.indexOf('\n\n')) >= 0) {
          const chunk = buffer.slice(0, idx)
          buffer = buffer.slice(idx + 2)
          parseChunk(chunk)
        }
      }
      if (!closed) {
        throw new Error('SSE closed')
      }
    } catch (e) {
      if (!closed && e.name !== 'AbortError') {
        onError && onError(e)
      }
    }
  }

  run()

  return {
    close() {
      closed = true
      controller.abort()
    }
  }
}

// 智能客服配置
export function getCsConfig() {
  return request.get('/customer-service/config')
}

export function updateCsConfig(data) {
  return request.put('/customer-service/config', data)
}

// 快捷回复
export function listQuickReplies() {
  return request.get('/customer-service/quick-replies')
}

export function addQuickReply(data) {
  return request.post('/customer-service/quick-replies', data)
}

export function updateQuickReply(id, data) {
  return request.put(`/customer-service/quick-replies/${id}`, data)
}

export function deleteQuickReply(id) {
  return request.delete(`/customer-service/quick-replies/${id}`)
}

// 管理员质检
export function adminListSessions(params) {
  return request.get('/admin/customer-service/sessions', { params })
}

export function adminCsStats() {
  return request.get('/admin/customer-service/stats')
}
