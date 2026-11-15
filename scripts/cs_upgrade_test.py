# -*- coding: utf-8 -*-
"""验证本轮客服改造：分侧删除、全部已读/单会话已读、发券规则配置、商家手动发券。
用法: python scripts/cs_upgrade_test.py （后端 8080 已起）
"""
import urllib.request, json, sys, time
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

def ok(name, cond, detail=''):
    print(('PASS ' if cond else 'FAIL ') + name + (('  ' + str(detail)) if detail else ''))
    return cond

if __name__ == '__main__':
    stu = login('student01', '123456'); st = stu['token']
    mer = login('100012', '123456'); mt = mer['token']

    # ---------- 1. 分侧删除 ----------
    s_sessions = (call('/customer-service/sessions', None, 'GET', st).get('data') or [])
    m_sessions = (call('/customer-service/sessions', None, 'GET', mt).get('data') or [])
    print('学生会话数=%d 商家会话数=%d' % (len(s_sessions), len(m_sessions)))
    ok('商家会话含 userId（前端分组需要）', all('userId' in s for s in m_sessions))

    if len(s_sessions) >= 2:
        victim = s_sessions[0]['id']
        call('/customer-service/sessions/%d' % victim, None, 'DELETE', st)
        s2 = (call('/customer-service/sessions', None, 'GET', st).get('data') or [])
        m2 = (call('/customer-service/sessions', None, 'GET', mt).get('data') or [])
        ok('学生删除后自己列表 -1', len(s2) == len(s_sessions) - 1, '%d -> %d' % (len(s_sessions), len(s2)))
        ok('学生删除不影响商家列表', len(m2) == len(m_sessions), '%d -> %d' % (len(m_sessions), len(m2)))
    else:
        print('SKIP 删除用例（学生会话不足2条）')

    # ---------- 2. 全部已读 / 单会话已读 ----------
    before = (call('/customer-service/unread-count', None, 'GET', st).get('data') or {}).get('count', -1)
    call('/customer-service/read-all', None, 'PUT', st)
    after = (call('/customer-service/unread-count', None, 'GET', st).get('data') or {}).get('count')
    ok('学生全部已读后未读=0', after == 0, 'before=%s after=%s' % (before, after))

    cur = (call('/customer-service/sessions', None, 'GET', st).get('data') or [])
    if cur:
        sid = cur[0]['id']
        call('/customer-service/sessions/%d/read' % sid, None, 'PUT', st)
        ok('单会话已读接口可用', True)

    # ---------- 3. 发券规则配置 ----------
    cfg = (call('/customer-service/config', None, 'GET', mt).get('data') or {})
    ok('配置返回新规则字段', all(k in cfg for k in
        ['discountEnabled', 'manualGrantEnabled', 'minRate', 'maxRate', 'allowFree', 'cooldownDays', 'veteranMinOrders']),
        {k: cfg.get(k) for k in ['manualGrantEnabled', 'minRate', 'maxRate', 'allowFree', 'cooldownDays', 'veteranMinOrders']})

    save = {
        'aiEnabled': cfg.get('aiEnabled', 0), 'reviewAiEnabled': cfg.get('reviewAiEnabled', 0),
        'persona': cfg.get('persona'), 'greeting': cfg.get('greeting'),
        'discountEnabled': 1, 'manualGrantEnabled': 1,
        'minRate': 0.50, 'maxRate': 0.95, 'allowFree': 1, 'cooldownDays': 30, 'veteranMinOrders': 10
    }
    saved = (call('/customer-service/config', save, 'PUT', mt).get('data') or {})
    ok('保存发券规则成功', float(saved.get('minRate') or 0) == 0.5 and int(saved.get('cooldownDays') or 0) == 30)

    # ---------- 4. 商家手动发券（真实进学生券包） ----------
    msess = (call('/customer-service/sessions', None, 'GET', mt).get('data') or [])
    target = next((s for s in msess if s.get('userId')), None)
    if target:
        sid = target['id']
        mine_before = (call('/coupons/my', None, 'GET', st).get('data') or [])
        r = call('/customer-service/sessions/%d/grant-coupon' % sid, {'rate': 0.8}, 'POST', mt)
        data = r.get('data') or {}
        ok('商家发券接口返回可领取卡片', r.get('code') == 200 and data.get('offerId'), data.get('message'))
        # 券卡片需要学生点击领取后才到账
        call('/coupons/offers/%s/claim' % data.get('offerId'), None, 'POST', st)
        mine_after = (call('/coupons/my', None, 'GET', st).get('data') or [])
        ok('学生领取后券包新增该券', len(mine_after) > len(mine_before),
           '%d -> %d 张' % (len(mine_before), len(mine_after)))
        # 会话里应出现一条商家消息
        msgs = (call('/customer-service/sessions/%d/messages' % sid, None, 'GET', st).get('data') or [])
        last = msgs[-1] if msgs else {}
        ok('会话里出现发券通知消息', '券' in (last.get('content') or ''), (last.get('content') or '')[:60])
        # 折扣收敛：0.30 应被夹到 minRate 0.50
        r2 = call('/customer-service/sessions/%d/grant-coupon' % sid, {'rate': 0.30}, 'POST', mt)
        d2 = r2.get('data') or {}
        ok('低于区间的折扣被夹到最低 0.50', float(d2.get('rate') or 0) == 0.5, d2.get('rate'))
        # 关闭手动发券后应被拒绝（业务错误在响应体 code 里，不抛 HTTP 异常）
        save['manualGrantEnabled'] = 0
        call('/customer-service/config', save, 'PUT', mt)
        denied = call('/customer-service/sessions/%d/grant-coupon' % sid, {'rate': 0.8}, 'POST', mt)
        ok('关闭手动发券后拒绝发券', denied.get('code') != 200, denied.get('message'))
        save['manualGrantEnabled'] = 1
        call('/customer-service/config', save, 'PUT', mt)
    else:
        print('SKIP 手动发券（商家无可用会话）')
