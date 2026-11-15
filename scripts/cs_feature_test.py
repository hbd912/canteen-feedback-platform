# -*- coding: utf-8 -*-
"""新功能端到端初测：多会话新建 + AI代回(熟客记忆) + 老客安抚发券 + 商家客户分层统计。
前提：后端已起、本地 MySQL。用法: python scripts/cs_feature_test.py
"""
import urllib.request, json, time, sys
sys.stdout.reconfigure(encoding='utf-8')
BASE = 'http://localhost:8080/api'

student_token = ''
merchant_token = ''
def call(path, payload=None, method=None, token=None):
    data = None
    headers = {'Authorization': 'Bearer ' + (token or '')}
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

def login(username, password):
    r = post('/auth/login', {'username': username, 'password': password})
    return (r.get('data') or {}).get('token', '')

def last_ai(sid, token):
    ds = (get('/customer-service/sessions/%d/messages' % sid, token)).get('data') or []
    for x in reversed(ds):
        if x.get('senderType') == 'AI':
            return x.get('content'), x
    return None, None

if __name__ == '__main__':
    student_token = login('student01', '123456')
    merchant_token = login('100012', '123456')
    print('student_token_len=%d merchant_len=%d' % (len(student_token), len(merchant_token)))

    # 1) 新建会话（多会话：强制新建）
    ns = post('/customer-service/sessions', {'stallId': 1, 'newSession': True}, student_token)
    sid = (ns.get('data') or {}).get('id')
    print('新建会话 id=%s' % sid)

    # 2) 发一条会触调解散情绪/安抚的问题，看 AI 是否给出「给券：」标记、是否发券
    print('\n[2] 发一条「老顾客遇到问题」的消息……')
    post('/customer-service/sessions/%d/messages' % sid,
         {'content': '老板我今天特别不顺，等了我好久，你这家我常来，心里真不舒服。'}, student_token)
    time.sleep(5)
    ai, vo = last_ai(sid, student_token)
    print('AI 回复:', ai)
    print('AI dishId=', (vo or {}).get('dishId'), '| 内容含给券?', ('给券' in (ai or '')))

    # 3) 商家客服建议（AI 帮我想），验证熟客档案注入不报错
    print('\n[3] 商家 AI 说一句话……')
    try:
        sug = post('/customer-service/sessions/%d/ai-suggest' % sid, None, merchant_token)
        print('suggest:', (sug.get('data') or {}).get('reply'))
    except Exception as e:
        print('suggest error:', e)

    # 4) 商家客户分层统计
    print('\n[4] 商家客户频次统计……')
    st = get('/merchant/stats/customers', merchant_token).get('data') or {}
    print('总学生=%s 完成订单=%s 新客=%s 常客=%s 老客=%s 已发安抚券=%s' % (
        st.get('totalStudents'), st.get('totalDoneOrders'), st.get('newCount'),
        st.get('regularCount'), st.get('veteranCount'), st.get('discountsGranted')))
    top = (st.get('students') or [])[:3]
    for it in top:
        print('  学生%s 单量=%s 类型=%s' % (it.get('studentName'), it.get('doneCount'), it.get('tier')))