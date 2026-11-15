# -*- coding: utf-8 -*-
"""验证「AI 一键代理」已经改成异步：POST 立即返回(不卡15s超时)，后台跑完可轮询 status。
用法: python scripts/todo_async_test.py
"""
import urllib.request, json, time, sys, datetime
sys.stdout.reconfigure(encoding='utf-8')
BASE = 'http://localhost:8080/api'

def call(path, payload=None, method=None, token=''):
    data = None
    headers = {'Authorization': 'Bearer ' + token}
    if payload is not None:
        data = json.dumps(payload).encode('utf-8')
        headers['Content-Type'] = 'application/json'
    req = urllib.request.Request(BASE + path, data=data, method=method, headers=headers)
    with urllib.request.urlopen(req) as r:
        return json.load(r)

def post(path, payload, token=''):
    return call(path, payload, 'POST', token)
def get(path, token=''):
    return call(path, None, 'GET', token)

if __name__ == '__main__':
    m = post('/auth/login', {'username': '100012', 'password': '123456'})
    tok = (m.get('data') or {}).get('token', '')
    print('merchant token_len=%d' % len(tok))

    # 1) POST 应立即返回（之前这里会同步跑到 15s 超时）
    t0 = time.time()
    r = post('/merchant/todos/ai-process', None, tok)
    post_elapsed = time.time() - t0
    print('POST ai-process 返回=%s  耗时=%.2fs（应 <2s，异步已生效）' % (r.get('code'), post_elapsed))

    # 2) 立即查状态：应 running=true
    st = (get('/merchant/todos/ai-process/status', tok).get('data') or {})
    print('立即状态: running=%s' % st.get('running'))

    # 3) 轮询到跑完
    done = False
    for _ in range(15):
        time.sleep(2)
        st = (get('/merchant/todos/ai-process/status', tok).get('data') or {})
        if not st.get('running'):
            done = True; break
    print('后台跑完?', done)
    rep = st.get('report') or {}
    print('orderDone=%s csReplied=%s reviewReplied=%s refundSkipped=%s' % (
        rep.get('orderDone'), rep.get('csReplied'), rep.get('reviewReplied'), rep.get('refundSkipped')))
    print('error=%s' % st.get('error'))
    print('summary=%s' % (rep.get('summary') or ''))