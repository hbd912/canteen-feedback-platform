# -*- coding: utf-8 -*-
"""学生端优惠券闭环：我的券 -> 加购 -> 结算选券 -> 订单抵扣 -> 券变已用。
用法: python scripts/coupon_checkout_test.py
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

def post(p, payload, t=''): return call(p, payload, 'POST', t)
def get(p, t=''): return call(p, None, 'GET', t)
def delete(p, t=''): return call(p, None, 'DELETE', t)

def ok(name, cond, detail=''):
    print(('PASS ' if cond else 'FAIL ') + name + (('  ' + str(detail)) if detail else ''))
    return cond

if __name__ == '__main__':
    st = (post('/auth/login', {'username': 'student01', 'password': '123456'}).get('data') or {})['token']

    # 清空购物车，加一个档口1的菜
    delete('/cart', st)
    post('/cart/items', {'dishId': 1, 'quantity': 1}, st)
    cart = (get('/cart', st).get('data') or {})
    items = [i for g in (cart.get('groups') or []) for i in (g.get('items') or [])]
    item = next((i for i in items if i.get('stallId') == 1), None)
    print('购物车条目:', item.get('dishName'), '价格=', item.get('price'), 'id=', item.get('id'))
    subtotal = float(item.get('price') or 0)

    # 我的券里挑一张可用
    mine = (get('/coupons/my', st).get('data') or [])
    usable = [c for c in mine
              if c.get('status') == 'UNUSED' and c.get('stallId') == 1
              and float(c.get('minAmount') or 0) <= subtotal]
    print('可用券:', [(c.get('name'), c.get('type'), c.get('value')) for c in usable])
    ok('学生能在「我的优惠券」看到可用券', len(usable) > 0, '%d 张' % len(usable))
    if not usable:
        sys.exit(0)
    coupon = usable[0]
    coupon_face = float(coupon.get('value') or 0)

    pickup = (datetime.datetime.now() + datetime.timedelta(minutes=30)).strftime('%Y-%m-%d %H:%M:%S')
    r = post('/cart/checkout', {
        'pickupTime': pickup,
        'itemIds': [item['id']],
        'userCouponId': coupon['id']
    }, st)
    ok('带券结算成功', r.get('code') == 200, r.get('message'))
    data = r.get('data')
    vo = data[0] if isinstance(data, list) and data else (data or {})
    print('结算返回:', json.dumps(vo, ensure_ascii=False)[:300])

    # 订单金额与抵扣
    order = (get('/orders/%s' % vo.get('orderId'), st).get('data') or {})
    print('订单:', {k: order.get(k) for k in ['orderNo', 'totalAmount', 'discountAmount', 'userCouponId']})
    ok('订单记录抵扣金额', float(order.get('discountAmount') or 0) > 0, order.get('discountAmount'))
    ok('订单记录使用的券', order.get('couponId') is not None, 'couponId=%s' % order.get('couponId'))

    # 券状态变已用
    mine2 = (get('/coupons/my', st).get('data') or [])
    used = next((c for c in mine2 if c.get('id') == coupon['id']), {})
    ok('券状态变为已使用', used.get('status') == 'USED', used.get('status'))
