# -*- coding: utf-8 -*-
"""验证「口头承诺打折/免单」剔除守卫：不开券时正文不得出现 免费吃/半价/给 .* 打折 这类空头承诺。
发了券(couponRate!=null)时允许保留。用法: python scripts/cs_promise_guard_test.py
"""
import urllib.request, json, time, sys
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

def post(path, payload, token=''): return call(path, payload, 'POST', token)
def get(path, token=''): return call(path, None, 'GET', token)
def login(u, p):
    return (call('/auth/login', {'username': u, 'password': p}, 'POST')).get('data') or {}

import re
PROMISE = re.compile(r'免费吃|免单|半价|打折|(给|送|让)\s*你[^，。！？]*?(免费|折扣|折)')

if __name__ == '__main__':
    stu = login('student01', '123456')
    st = stu.get('token', '')
    # 新会话（强制）
    ns = post('/customer-service/sessions', {'stallId': 1, 'newSession': True}, st)
    sid = (ns.get('data') or {}).get('id')
    print('新会话 %s' % sid)

    # 给多条不同角度的抱怨，观察 AI 是否只通过「给券：」实现、正文不再空口承诺
    prompts = [
        '阿叔，我在你家吃了特别多次了，今天宿舍吵架心情差，菜还出得慢，你说怎么办。',
        '老板，老顾客了，今天真的被气到，从没这么失望，就想平复一下心情。',
        '这一个月天天光顾，今天特别委屈，排队好久还冷，能体谅下吗？',
    ]
    bad = []
    for i, q in enumerate(prompts):
        post('/customer-service/sessions/%d/messages' % sid, {'content': q}, st)
        time.sleep(7)
        ds = (get('/customer-service/sessions/%d/messages' % sid, token=st)).get('data') or []
        ai = None
        for x in reversed(ds):
            if x.get('senderType') == 'AI':
                ai = x.get('content'); break
        # 看这条 AI 是否带「给券：」
        gave = '给券' in (ai or '')
        # 正文里剔除给券标记行后，是否还有口头承诺
        body = re.sub(r'给券[：:][\d.]+[（(]?[^）)]*[）)]?', '', ai or '')
        clean = re.sub(r'给券[：:]', '', body)
        hits = PROMISE.findall(clean)
        print('prompt%d 发券=%s | 正文残留承诺=%s' % (i, gave, bool(hits)))
        if (not gave) and hits:
            bad.append((q, ai))
    print('\n违规(未发券却空口承诺)条数:', len(bad))
    for q, ai in bad[:3]:
        print('---\n原问题:', q)
        print('AI 回复:', ai)