# -*- coding: utf-8 -*-
"""AI 发券也走可领取卡片：AI 判定发券 -> 生成卡片(未领取) -> 学生领取后才到账。"""
import urllib.request, json, sys, time, subprocess
sys.stdout.reconfigure(encoding='utf-8')
B='http://localhost:8080/api'
def call(p,payload=None,method=None,t=''):
    d=None; h={'Authorization':'Bearer '+t}
    if payload is not None: d=json.dumps(payload).encode(); h['Content-Type']='application/json'
    return json.load(urllib.request.urlopen(urllib.request.Request(B+p,data=d,method=method or ('POST' if payload is not None else 'GET'),headers=h)))
def post(p,payload=None,t=''): return call(p,payload,'POST',t)
def get(p,t=''): return call(p,None,'GET',t)
def put(p,payload,t=''): return call(p,payload,'PUT',t)
def ok(n,c,d=''): print(('PASS ' if c else 'FAIL ')+n+(('  '+str(d)) if d else '')); return c
def sql(q): return subprocess.run(['mysql','-uroot','-p123456','canteen_feedback','-N','-e',q],capture_output=True,text=True,encoding='utf-8',errors='replace').stdout.strip()

if __name__=='__main__':
    st=(post('/auth/login',{'username':'student01','password':'123456'}).get('data') or {})['token']
    mt=(post('/auth/login',{'username':'100012','password':'123456'}).get('data') or {})['token']
    cfg=(get('/customer-service/config',mt).get('data') or {})
    save={k:cfg.get(k) for k in ['aiEnabled','reviewAiEnabled','persona','greeting']}
    save.update({'discountEnabled':1,'manualGrantEnabled':1,'minRate':0.50,'maxRate':0.95,
                 'allowFree':1,'cooldownDays':0,'veteranMinOrders':1})
    put('/customer-service/config',save,mt)

    def rows(): return int(sql("SELECT COUNT(*) FROM customer_service_discount WHERE stall_id=1 AND user_id=3;") or 0)
    before_rows=rows()
    wallet_before=len(get('/coupons/my',st).get('data') or [])
    ns=(post('/customer-service/sessions',{'stallId':1,'newSession':True},st).get('data') or {})
    sid=ns.get('id')
    prompts=[
      '老板，我在你家吃了特别多次了，今天心情特别差，菜还等了好久，能体谅下吗？',
      '老顾客了，今天真的被气到了，从没这么失望过，你说怎么办吧。',
      '阿叔，我天天来你家吃，今天特别委屈，宿舍一团糟，能安慰下我吗？',
      '老顾客了，这次真的很不开心，你就不能给点实惠安慰一下吗？',
      '老板，今天心情差到极点，我几乎天天来你家吃，就当照顾下老顾客吧。',
      '唉，今天太倒霉了，在你这吃了这么多次，这顿吃得一点都不顺心。',
    ]
    triggered=False
    for i in range(6):
        post('/customer-service/sessions/%d/messages'%sid,{'content':prompts[i%len(prompts)]},st)
        time.sleep(7)
        if rows()>before_rows: triggered=True; break
    ok('AI 按规则触发了发券', triggered, '%d -> %d'%(before_rows, rows()))

    msgs=get('/customer-service/sessions/%d/messages'%sid,st).get('data') or []
    card=None
    for m in reversed(msgs):
        if m.get('senderType')=='AI' and m.get('couponOfferId'):
            card=m; break
    ok('AI 回复带可领取券卡片', card is not None,
       card and {k:card.get(k) for k in ['couponOfferId','couponOfferStatus','couponName','couponValue']})
    wallet_mid=len(get('/coupons/my',st).get('data') or [])
    ok('未领取时券包不增加（AI 也不能空口发券）', wallet_mid==wallet_before, '%d -> %d'%(wallet_before,wallet_mid))
    if card:
        ok('AI 文案提示点卡片领取', '卡片' in (card.get('content') or ''), (card.get('content') or '')[-40:])
        r=post('/coupons/offers/%s/claim'%card['couponOfferId'],None,st)
        ok('学生领取 AI 券成功', r.get('code')==200, r.get('message'))
        wallet_after=len(get('/coupons/my',st).get('data') or [])
        ok('领取后券包 +1', wallet_after==wallet_before+1, '%d -> %d'%(wallet_before,wallet_after))

    save.update({'cooldownDays':30,'veteranMinOrders':10})
    put('/customer-service/config',save,mt)
    print('已恢复默认规则')
