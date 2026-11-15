# -*- coding: utf-8 -*-
"""老客安抚发券链路测试：新会话->老客诉苦->看AI是否给券->查学生券->再触发看冷却。
只读追加测试数据，可重复跑。
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

def login(u, p):
    return (call('/auth/login', {'username': u, 'password': p}, 'POST')).get('data') or {}

if __name__ == '__main__':
    stu = login('student01', '123456')
    st = stu.get('token', '')
    # student01 是老客(已完成12单)。开新会话，用会触发安抚的抱怨。
    ns = call('/customer-service/sessions', {'stallId': 1, 'newSession': True}, 'POST', st)
    sid = (ns.get('data') or {}).get('id')
    print('新会话 %s' % sid)

    # 多次尝试：模型是否会给券标记
    gave = None
    for attempt in range(3):
        q = ['老板我这个月天天来吃，今天宿舍里一团糟心情很差，菜还等了好久，真心酸。',
             '老顾客了，今天真的被气到了，从没这么失望过，你说怎么办吧。',
             '阿叔，我在你这吃了几十次了，今天到处碰壁，就想吃点顺心的，能体谅一下吗？'][attempt]
        call('/customer-service/sessions/%d/messages' % sid, {'content': q}, 'POST', st)
        time.sleep(6)
        ds = (call('/customer-service/sessions/%d/messages' % sid, token=st)).get('data') or []
        ai = None
        for x in reversed(ds):
            if x.get('senderType') == 'AI':
                ai = x.get('content'); break
        print('attempt%d AI: %s' % (attempt + 1, ai))
        if ai and '给券' in ai:
            gave = ai; break
        time.sleep(1)

    print('给券触发?', gave is not None)
    if gave:
        print('触发文案:', gave)

    # 无论是否触发，查学生的「我的券」看有无新到账的老顾客感谢券/免单券
    mine = call('/coupons/my', token=st).get('data') or []
    names = [c.get('name') for c in mine]
    print('学生当前我的券:', names)

    # 查后端发券流水 + 冷却
    # 通过 DB 独立确认
    import subprocess
    out = subprocess.run(['mysql', '-uroot', '-p123456', 'canteen_feedback', '-N', '-e',
        'SELECT id,stall_id,user_id,tier,discount_value,coupon_id,user_coupon_id FROM customer_service_discount ORDER BY id DESC LIMIT 5;'],
        capture_output=True, text=True, encoding='utf-8', errors='replace').stdout
    print('发券流水(DB):\n' + (out or '(空)'))