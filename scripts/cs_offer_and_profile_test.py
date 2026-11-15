# -*- coding: utf-8 -*-
"""本轮验证：商家个人中心对账、删除会话后消息恢复、客服券卡片领取、单份菜券结算。
用法: python scripts/cs_offer_and_profile_test.py
"""
import urllib.request, json, sys, time, datetime
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

def post(p, payload=None, t=''): return call(p, payload, 'POST', t)
def get(p, t=''): return call(p, None, 'GET', t)
def put(p, payload=None, t=''): return call(p, payload, 'PUT', t)
def delete(p, t=''): return call(p, None, 'DELETE', t)

def ok(name, cond, detail=''):
    print(('PASS ' if cond else 'FAIL ') + name + (('  ' + str(detail)) if detail else ''))
    return cond

if __name__ == '__main__':
    student = post('/auth/login', {'username': 'student01', 'password': '123456'}).get('data') or {}
    merchant = post('/auth/login', {'username': '100012', 'password': '123456'}).get('data') or {}
    st, mt = student['token'], merchant['token']

    # ---------- 1. 商家个人中心与财务/销售对账 ----------
    acc = (get('/auth/account-summary', st).get('data') or {})
    macc = (get('/auth/account-summary', mt).get('data') or {})
    fin = (get('/merchant/finance/summary', mt).get('data') or {})
    sales = (get('/merchant/sales/summary', mt).get('data') or {})
    print('商家 account-summary:', {k: macc.get(k) for k in
        ['merchant', 'businessIncome', 'available', 'monthIncome', 'businessOrderCount', 'businessCustomerCount']})
    ok('商家个人中心标记为商家', macc.get('merchant') is True)
    ok('个人中心累计收益=财务中心', str(macc.get('businessIncome')) == str(fin.get('totalIncome')))
    ok('个人中心累计收益=销售情况', str(macc.get('businessIncome')) == str(sales.get('totalIncome')))
    ok('个人中心订单数=销售情况', str(macc.get('businessOrderCount')) == str(sales.get('orderCount')))
    ok('个人中心可提现=财务中心', str(macc.get('available')) == str(fin.get('available')))

    # ---------- 2. 删除会话后学生发消息，商家应重新收到 ----------
    ns = (post('/customer-service/sessions', {'stallId': 1, 'newSession': True}, st).get('data') or {})
    sid = ns.get('id')
    delete('/customer-service/sessions/%d' % sid, mt)
    ml = (get('/customer-service/sessions', mt).get('data') or [])
    ok('商家删除后列表隐藏该会话', all(s['id'] != sid for s in ml))
    post('/customer-service/sessions/%d/messages' % sid, {'content': '老板，我又来问啦，还有饭吗？'}, st)
    ml2 = (get('/customer-service/sessions', mt).get('data') or [])
    found = next((s for s in ml2 if s['id'] == sid), None)
    ok('学生再发消息后商家重新收到该会话', found is not None,
       'unread=%s last=%s' % ((found or {}).get('merchantUnread'), (found or {}).get('lastMessage')))

    # ---------- 3. 客服发券 -> 可领取卡片 -> 学生领取 ----------
    # 先确保商家手动发券开启
    cfg = (get('/customer-service/config', mt).get('data') or {})
    save = {k: cfg.get(k) for k in ['aiEnabled', 'reviewAiEnabled', 'persona', 'greeting']}
    save.update({'discountEnabled': 1, 'manualGrantEnabled': 1, 'minRate': 0.50,
                 'maxRate': 0.95, 'allowFree': 1, 'cooldownDays': 30, 'veteranMinOrders': 10})
    put('/customer-service/config', save, mt)

    before = len(get('/coupons/my', st).get('data') or [])
    r = post('/customer-service/sessions/%d/grant-coupon' % sid, {'rate': 0.8, 'scope': 'ORDER'}, mt).get('data') or {}
    offer_id = r.get('offerId')
    ok('商家发券返回可领取 offer', offer_id is not None, r.get('message'))
    mine = get('/coupons/my', st).get('data') or []
    ok('未领取时券包不增加（学生不点就收不到）', len(mine) == before, '%d -> %d' % (before, len(mine)))
    msgs = (get('/customer-service/sessions/%d/messages' % sid, st).get('data') or [])
    card = next((m for m in reversed(msgs) if m.get('couponOfferId') == offer_id), None)
    ok('会话里出现券卡片(UNCLAIMED)', card and card.get('couponOfferStatus') == 'UNCLAIMED',
       card and {k: card.get(k) for k in ['couponName', 'couponValue', 'couponScope']})
    first = post('/coupons/offers/%d/claim' % offer_id, None, st)
    ok('学生领取成功', first.get('code') == 200, first.get('message'))
    mine2 = get('/coupons/my', st).get('data') or []
    ok('领取后券包 +1', len(mine2) == before + 1, '%d -> %d' % (before, len(mine2)))
    second = post('/coupons/offers/%d/claim' % offer_id, None, st)
    ok('重复领取被拒绝', second.get('code') != 200, second.get('message'))
    msgs2 = (get('/customer-service/sessions/%d/messages' % sid, st).get('data') or [])
    card2 = next((m for m in reversed(msgs2) if m.get('couponOfferId') == offer_id), None)
    ok('券卡片状态变已领取', card2 and card2.get('couponOfferStatus') == 'CLAIMED')

    # ---------- 4. 单份菜券：只抵扣一道菜的一份 ----------
    # 商家发一张「限宫保鸡丁(dish 1) 5折」券
    r2 = post('/customer-service/sessions/%d/grant-coupon' % sid,
              {'rate': 0.5, 'scope': 'ITEM', 'dishId': 1}, mt).get('data') or {}
    item_offer = r2.get('offerId')
    post('/coupons/offers/%d/claim' % item_offer, None, st)
    mine3 = get('/coupons/my', st).get('data') or []
    item_coupon = next((c for c in mine3 if c.get('scope') == 'ITEM' and c.get('dishId') == 1
                        and c.get('status') == 'UNUSED'), None)
    ok('单份菜券进入券包并带适用范围', item_coupon is not None,
       item_coupon and {k: item_coupon.get(k) for k in ['name', 'scope', 'dishName']})

    # 购物车：宫保鸡丁 12 x1 + 另一道菜 9 x1，用单菜券结算
    delete('/cart', st)
    post('/cart/items', {'dishId': 1, 'quantity': 1}, st)
    post('/cart/items', {'dishId': 5, 'quantity': 1}, st)
    cart = (get('/cart', st).get('data') or {})
    items = [i for g in (cart.get('groups') or []) for i in (g.get('items') or [])]
    subtotal = sum(float(i.get('price') or 0) * int(i.get('quantity') or 1) for i in items)
    print('购物车小计:', subtotal, '| 条目:', [(i.get('dishName'), i.get('price'), i.get('quantity')) for i in items])
    pickup = (datetime.datetime.now() + datetime.timedelta(minutes=30)).strftime('%Y-%m-%d %H:%M:%S')
    co = post('/cart/checkout', {
        'pickupTime': pickup,
        'itemIds': [i['id'] for i in items],
        'userCouponId': item_coupon['id']
    }, st)
    vo = (co.get('data') or [{}])[0] if isinstance(co.get('data'), list) else (co.get('data') or {})
    order = (get('/orders/%s' % vo.get('orderId'), st).get('data') or {})
    print('单菜券订单:', {k: order.get(k) for k in ['totalAmount', 'discountAmount']})
    # 宫保鸡丁 12 元 5 折 -> 只减 6 元（而不是按 21 元小计减 10.5）
    ok('单份菜券只抵扣一道菜的一份', abs(float(order.get('discountAmount') or 0) - 6.0) < 0.01,
       'discount=%s (期望6.00)' % order.get('discountAmount'))
