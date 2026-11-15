# -*- coding: utf-8 -*-
"""验证部分退款细分：学生拉订单列表，检查订单25441/25438是否带 partialRefund + item.refunded。"""
import urllib.request, json, sys
sys.stdout.reconfigure(encoding='utf-8')
sys.path.insert(0, 'scripts')
import cs_test as m

resp = m.call('/orders/my?current=1&size=20')
ds = resp.get('data') or {}
recs = ds.get('records') or []
targets = {25441, 25438, 25433}
for o in recs:
    if o.get('id') in targets:
        print(f"订单 {o['id']} statusLabel={o.get('statusLabel')} partialRefund={o.get('partialRefund')} refundedItemIds={o.get('refundedItemIds')} payStatusLabel={o.get('payStatusLabel')}")
        for it in (o.get('items') or []):
            print(f"   item id={it['id']} {it['dishName']} x{it['quantity']} refunded={it.get('refunded')}")
        print('---')
