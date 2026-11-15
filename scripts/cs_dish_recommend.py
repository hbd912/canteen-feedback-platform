# -*- coding: utf-8 -*-
"""验证AI推荐菜 -> 文本 + dishId(菜品卡)。"""
import urllib.request, json, time, sys
sys.stdout.reconfigure(encoding='utf-8')
sys.path.insert(0, 'scripts')
import cs_test as m

def latest_ai():
    resp = m.call('/customer-service/sessions/1/messages')
    ms = resp.get('data') or []
    for x in reversed(ms):
        if x.get('senderType') == 'AI':
            return x
    return None

print('>>> 发: 你们家有什么招牌菜推荐吗？')
m.send('你们家有什么招牌菜推荐吗？')
time.sleep(6)
ai = latest_ai()
if ai is None:
    print('  无AI回复')
else:
    print('AI content:', ai.get('content'))
    print('AI dishId :', ai.get('dishId'))
    print('AI dishName:', ai.get('dishName'))
    print('AI dishPrice:', ai.get('dishPrice'))
    print('AI dishImage:', (ai.get('dishImage') or '')[:40])
