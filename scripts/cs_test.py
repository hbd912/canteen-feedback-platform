# -*- coding: utf-8 -*-
"""客服异步代回实测：登录 -> 发消息 -> 测响应延迟 -> 拉最新AI回复。"""
import urllib.request, json, time, sys
sys.stdout.reconfigure(encoding='utf-8')

BASE = 'http://localhost:8080/api'

def call(path, payload=None, method=None):
    data = None
    headers = {'Authorization': 'Bearer ' + TOKEN} if TOKEN else {}
    if payload is not None:
        data = json.dumps(payload).encode('utf-8')
        headers['Content-Type'] = 'application/json'
    req = urllib.request.Request(BASE + path, data=data, method=method, headers=headers)
    with urllib.request.urlopen(req) as r:
        return json.load(r)

# 登录
TOKEN = ''
lg = call('/auth/login', {'username': 'student01', 'password': '123456'}, 'POST')
TOKEN = (lg.get('data') or {}).get('token', '')
print('login token_len=', len(TOKEN))

def send(content):
    t = time.time()
    resp = call('/customer-service/sessions/1/messages', {'content': content}, 'POST')
    dt = (time.time() - t) * 1000
    print('HTTP_MS=%.0f code=%s' % (dt, resp.get('code')))
    ds = resp.get('data') or []
    if ds:
        last = ds[-1]
        print('  resp last sender=%s | %s' % (last.get('senderType'), last.get('content')))

def pull():
    resp = call('/customer-service/sessions/1/messages')
    ds = resp.get('data') or []
    if not ds:
        print('  (empty)')
        return
    last = ds[-1]
    print('  [pull] sender=%s | %s' % (last.get('senderType'), last.get('content')))

if __name__ == '__main__':
    print('>>> 发: 你是谁')
    send('你是谁')
    time.sleep(3)
    print('--- 3s 后拉取 ---')
    pull()