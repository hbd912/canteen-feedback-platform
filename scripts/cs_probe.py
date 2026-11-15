# -*- coding: utf-8 -*-
"""连续发几个不同问题，观察AI代回是否自然、是否对题、不客套。"""
import sys, time; sys.path.insert(0, 'scripts')
import cs_test as m

def latest_ai():
    resp = m.call('/customer-service/sessions/1/messages')
    for x in reversed(resp.get('data') or []):
        if x.get('senderType') == 'AI':
            return x.get('content')
    return None

questions = [
    '你们家回锅肉多少钱一份？',
    '现在点餐多久能出餐？我等下要去上课',
    '你们这有辣的菜推荐吗，我比较能吃辣',
]
for q in questions:
    m.send(q)
    # 等后台异步 + 生成 3~4 秒
    time.sleep(4)
    ai = latest_ai()
    print('Q:', q)
    print('A:', ai)
    print('-' * 40)