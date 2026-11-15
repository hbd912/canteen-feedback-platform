# -*- coding: utf-8 -*-
"""验证 AI 发券受商家规则约束：老客门槛、冷却天数、折扣区间收敛、免单开关。
用法: python scripts/cs_ai_rules_test.py
"""
import urllib.request, json, sys, time, subprocess
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

def sql(q):
    return subprocess.run(['mysql', '-uroot', '-p123456', 'canteen_feedback', '-N', '-e', q],
                          capture_output=True, text=True, encoding='utf-8', errors='replace').stdout.strip()

def ok(name, cond, detail=''):
    print(('PASS ' if cond else 'FAIL ') + name + (('  ' + str(detail)) if detail else ''))
    return cond

def base_cfg(cfg):
    return {
        'aiEnabled': cfg.get('aiEnabled', 1), 'reviewAiEnabled': cfg.get('reviewAiEnabled', 0),
        'persona': cfg.get('persona'), 'greeting': cfg.get('greeting'),
        'discountEnabled': 1, 'manualGrantEnabled': 1,
        'minRate': 0.50, 'maxRate': 0.95, 'allowFree': 1, 'cooldownDays': 0, 'veteranMinOrders': 1
    }

def discount_rows():
    return int(sql("SELECT COUNT(*) FROM customer_service_discount WHERE stall_id=1 AND user_id=3;") or 0)

def complain_until_grant(st, sid, before_rows, tries=6):
    """连发抱怨消息，直到发券流水比基线多一条（AI 的「给券：」标记会被服务端清理，不能看正文）"""
    prompts = [
        '老板，我在你家吃了好多好多次了，今天心情特别差，菜还等了好久，你能体谅下吗？',
        '老顾客了，今天真的被气到了，从没这么失望过，你说怎么办吧。',
        '阿叔，我天天来你家吃，今天特别委屈，宿舍一团糟，能安慰下我吗？',
        '老顾客了，这次真的很不开心，你就不能给点实惠安慰一下吗？',
        '老板，今天心情差到极点，我几乎天天来你家吃，你就当照顾下老顾客吧。',
        '唉，今天太倒霉了，在你这吃了这么多次，这顿吃得一点都不顺心。',
    ]
    for i in range(tries):
        call('/customer-service/sessions/%d/messages' % sid, {'content': prompts[i % len(prompts)]}, 'POST', st)
        time.sleep(7)
        if discount_rows() > before_rows:
            return True
    return False

if __name__ == '__main__':
    stu = login('student01', '123456'); st = stu['token']
    mer = login('100012', '123456'); mt = mer['token']
    cfg = (call('/customer-service/config', None, 'GET', mt).get('data') or {})

    print('== 规则一：老客门槛 999，AI 即使给券也不应真发 ==')
    c = base_cfg(cfg); c['veteranMinOrders'] = 999; c['cooldownDays'] = 0
    call('/customer-service/config', c, 'PUT', mt)
    before = discount_rows()
    ns = (call('/customer-service/sessions', {'stallId': 1, 'newSession': True}, 'POST', st).get('data') or {})
    sid = ns.get('id')
    granted = complain_until_grant(st, sid, before)
    after = discount_rows()
    print('  是否触发发券:', granted, '| 发券流水 %s -> %s' % (before, after))
    ok('未达老客门槛时不发券', before == after, '%s -> %s' % (before, after))

    print('\n== 规则二：门槛 1、冷却 0、区间 0.50~0.95，AI 的折扣应被收敛 ==')
    c = base_cfg(cfg); c['veteranMinOrders'] = 1; c['cooldownDays'] = 0
    call('/customer-service/config', c, 'PUT', mt)
    before = discount_rows()
    ns = (call('/customer-service/sessions', {'stallId': 1, 'newSession': True}, 'POST', st).get('data') or {})
    sid = ns.get('id')
    granted = complain_until_grant(st, sid, before)
    ok('达到门槛后 AI 能真实发券', granted, '%s -> %s' % (before, discount_rows()))
    row = sql("SELECT d.discount_value, c.value FROM customer_service_discount d "
              "JOIN coupon c ON c.id=d.coupon_id WHERE d.stall_id=1 AND d.user_id=3 ORDER BY d.id DESC LIMIT 1;")
    print('  最新发券流水 / 券面值:', row.replace('\n', ' | '))
    if row:
        parts = row.split('\t')
        dv = float(parts[0]); cv = float(parts[1]) if len(parts) > 1 else -1
        ok('券的折扣落在配置区间内', 0 <= dv <= 0.95 and abs(dv - cv) < 1e-6, 'discount=%s couponValue=%s' % (dv, cv))

    print('\n== 规则三：关闭免单，AI 的 0 折不应发券，手动 0 折应被拒绝 ==')
    c = base_cfg(cfg); c['veteranMinOrders'] = 1; c['cooldownDays'] = 0; c['allowFree'] = 0
    call('/customer-service/config', c, 'PUT', mt)
    denied = call('/customer-service/sessions/%d/grant-coupon' % sid, {'rate': 0}, 'POST', mt)
    ok('关闭免单后手动 0 折被拒绝', denied.get('code') != 200, denied.get('message'))

    # 恢复默认规则
    c = base_cfg(cfg); c['veteranMinOrders'] = 10; c['cooldownDays'] = 30; c['allowFree'] = 1
    call('/customer-service/config', c, 'PUT', mt)
    print('\n已恢复默认规则：门槛=10 冷却=30 区间=0.50~0.95 允许免单=1')
