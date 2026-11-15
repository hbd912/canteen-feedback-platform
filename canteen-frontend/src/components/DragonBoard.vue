<template>
  <div class="board">
    <section class="col">
      <header class="col-head hot">
        <span class="ico"></span>
        <h3>爆款热菜</h3>
        <span class="rule">按点单数 · 好评率 ≥ 60%</span>
      </header>
      <ul class="list">
        <li v-for="(d, i) in hotDishes" :key="d.dishId" class="card hot">
          <span class="no">{{ i + 1 }}</span>
          <div class="thumb">
            <img v-if="!broken[d.dishId]" :src="d.image" alt="" @error="broken[d.dishId] = true" />
            <span v-else>{{ d.dishName.slice(0, 1) }}</span>
          </div>
          <div class="main">
            <div class="line1">
              <b>{{ d.dishName }}</b>
              <span class="star">★ {{ d.avgRating }}</span>
            </div>
            <div class="line2">{{ d.stallName }} · {{ d.windowNo }} · ¥{{ d.price }}</div>
            <div class="rate">
              <div class="rate-bar"><i :style="{ width: Math.min(d.goodRate, 100) + '%' }"></i></div>
              <em>好评 <b>{{ Math.round(d.goodRate) }}%</b></em>
            </div>
          </div>
          <div class="sales">
            <b>{{ splitCount(d.salesCount).num }}<i>{{ splitCount(d.salesCount).unit }}</i></b>
            <em>份已售</em>
          </div>
        </li>
        <li v-if="!hotDishes.length" class="empty">暂无热销数据</li>
      </ul>
    </section>

    <div class="split"></div>

    <section class="col">
      <header class="col-head treasure">
        <span class="ico"></span>
        <h3>宝藏档口</h3>
        <span class="rule">{{ formula }}</span>
      </header>
      <ul class="list">
        <li v-for="(s, i) in treasureStalls" :key="s.stallId" class="card treasure">
          <span class="no">{{ i + 1 }}</span>
          <div class="thumb" :class="{ initial: broken[s.stallId] || !coverUrl(s) }">
            <img v-if="!broken[s.stallId] && coverUrl(s)" :src="coverUrl(s)" :alt="s.stallName" @error="broken[s.stallId] = true" />
            <span v-else>{{ s.stallName.slice(0, 1) }}</span>
          </div>
          <div class="main">
            <div class="line1">
              <b>{{ s.stallName }}</b>
              <span class="score">{{ s.composite }}</span>
            </div>
            <div class="line2">{{ s.windowNo }} · {{ s.stallZone }} · {{ s.dishCount }} 道菜</div>
            <div class="metrics">
              <span class="tag">单 {{ fmtCount(s.salesTotal) }}</span>
              <span class="tag">★ {{ s.avgRating }}</span>
              <span class="tag">卫 {{ s.hygieneScore }}</span>
            </div>
          </div>
        </li>
        <li v-if="!treasureStalls.length" class="empty">暂无档口数据</li>
      </ul>
    </section>
  </div>
</template>

<script setup>
import { reactive, computed } from 'vue'

const props = defineProps({
  data: { type: Object, default: () => ({}) }
})

const broken = reactive({})
const hotDishes = computed(() => props.data.hotDishes || [])
const treasureStalls = computed(() => props.data.treasureStalls || [])
const formula = computed(() => props.data.formula || '点单 30% · 评分 35% · 卫生 35%')
const fmtCount = n => (n >= 10000 ? (n / 10000).toFixed(1) + ' 万' : (n ?? 0))
const splitCount = n => (n >= 10000 ? { num: (n / 10000).toFixed(1), unit: '万' } : { num: n ?? 0, unit: '' })
/** 宝藏档口名牌：优先用档口门头图（与美食探索档口列表同源，/ui/* 由前端静态目录服务），加载失败回退名字首字 */
function coverUrl(s) {
  return s.coverImage || ''
}
</script>

<style scoped>
.board {
  display: flex;
  flex: 1 1 0;
  width: 100%;
  min-height: 0;
  padding: 2px 4px 0;
}

.col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  /* 以列宽为查询基准：大屏缩窗/嵌入模式时按下方 @container 分级收缩，防固定尺寸溢出穿模 */
  container-type: inline-size;
}

.split {
  width: 1px;
  margin: 4px 16px;
  background: linear-gradient(180deg, transparent, rgba(255, 214, 172, 0.26), transparent);
}

.col-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 2px 8px;
}

.col-head .ico {
  width: 4px;
  height: 15px;
  border-radius: 2px;
}

.col-head.hot .ico {
  background: linear-gradient(180deg, #ffcf8a, #ff8a3d);
  box-shadow: 0 0 8px rgba(255, 150, 80, 0.7);
}

.col-head.treasure .ico {
  background: linear-gradient(180deg, #ffe9b0, #d9a13a);
  box-shadow: 0 0 8px rgba(230, 180, 90, 0.65);
}

.col-head h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0.5px;
  white-space: nowrap;
}

.col-head.hot h3 {
  color: #ffd7a8;
}

.col-head.treasure h3 {
  color: #ffe6b8;
}

.rule {
  margin-left: auto;
  min-width: 0;
  font-size: 11px;
  color: rgba(255, 232, 206, 0.55);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
  min-height: 0;
}

.card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 10px 6px 8px;
  border-radius: 10px;
  border: 1px solid rgba(255, 210, 165, 0.12);
  background: linear-gradient(96deg, rgba(255, 205, 155, 0.1), rgba(255, 190, 135, 0.035));
  flex: 1 1 0;
  min-height: 0;
}

.card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 12%;
  bottom: 12%;
  width: 3px;
  border-radius: 0 3px 3px 0;
}

.card.hot::before {
  background: linear-gradient(180deg, #ffc07a, #f07a2a);
}

.card.treasure::before {
  background: linear-gradient(180deg, #ffe0a0, #d9a13a);
}

.no {
  position: relative;
  flex: none;
  width: 40px;
  text-align: center;
  font-size: 27px;
  font-weight: 900;
  font-style: italic;
  line-height: 1;
  letter-spacing: -1.5px;
  font-variant-numeric: tabular-nums;
  background: linear-gradient(165deg, #fff5da 6%, #ffb75c 52%, #dd7520 96%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  -webkit-text-fill-color: transparent;
  filter: drop-shadow(0 0 9px rgba(255, 146, 52, 0.55));
}

.card:nth-child(1) .no {
  width: 48px;
  font-size: 34px;
  letter-spacing: -2px;
}

.card:nth-child(-n + 3) .no::before {
  content: '';
  position: absolute;
  inset: -5px -6px;
  border-radius: 10px;
  background: radial-gradient(ellipse at 50% 46%, rgba(255, 172, 74, 0.3), rgba(255, 140, 50, 0.08) 62%, transparent 78%);
}

.card:nth-child(1) {
  background: linear-gradient(96deg, rgba(255, 214, 150, 0.2), rgba(255, 190, 135, 0.05));
  border-color: rgba(255, 204, 145, 0.26);
}

.card:nth-child(2),
.card:nth-child(3) {
  background: linear-gradient(96deg, rgba(255, 208, 158, 0.14), rgba(255, 190, 135, 0.04));
  border-color: rgba(255, 205, 160, 0.18);
}

.card.treasure .no {
  background: linear-gradient(165deg, #fffbe8 6%, #ffd977 52%, #c99327 96%);
  -webkit-background-clip: text;
  background-clip: text;
  filter: drop-shadow(0 0 9px rgba(230, 178, 70, 0.5));
}

.card:nth-child(n + 4) .no {
  background: linear-gradient(165deg, #f7dfb4 6%, #d79a4e 96%);
  -webkit-background-clip: text;
  background-clip: text;
  filter: drop-shadow(0 0 6px rgba(210, 140, 60, 0.28));
}

.thumb {
  width: 62px;
  height: 62px;
  flex: none;
  border-radius: 8px;
  overflow: hidden;
  display: grid;
  place-items: center;
  background: rgba(255, 200, 150, 0.14);
  font-size: 22px;
  font-weight: 700;
  color: rgba(255, 232, 206, 0.55);
}

.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.thumb.initial {
  background: linear-gradient(140deg, rgba(255, 214, 150, 0.26), rgba(200, 140, 70, 0.14));
  border: 1px solid rgba(255, 210, 160, 0.2);
  color: #ffe3b6;
  font-size: 24px;
}

.main {
  flex: 1;
  min-width: 0;
}

.line1 {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.line1 b {
  font-size: 15.5px;
  font-weight: 700;
  color: #fff4e6;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.star {
  margin-left: auto;
  flex: none;
  font-size: 16.5px;
  font-weight: 800;
  color: #ffd48e;
  font-variant-numeric: tabular-nums;
}

.score {
  margin-left: auto;
  flex: none;
  font-size: 26px;
  font-weight: 900;
  line-height: 1;
  letter-spacing: -0.8px;
  font-variant-numeric: tabular-nums;
  color: #ffe6b8;
  text-shadow: 0 0 12px rgba(255, 200, 110, 0.5);
}

.line2 {
  margin-top: 2px;
  font-size: 11.5px;
  color: rgba(255, 230, 205, 0.62);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.rate {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-top: 7px;
}

.rate-bar {
  flex: 1;
  height: 5px;
  border-radius: 3px;
  background: rgba(255, 220, 180, 0.13);
  overflow: hidden;
}

.rate-bar i {
  display: block;
  height: 100%;
  border-radius: 3px;
  background: linear-gradient(90deg, rgba(255, 190, 110, 0.65), #ffc06a);
}

.rate em {
  flex: none;
  font-size: 11px;
  line-height: 1;
  font-style: normal;
  color: rgba(255, 232, 206, 0.66);
  white-space: nowrap;
}

.rate em b {
  font-size: 15px;
  font-weight: 800;
  color: #ffdca8;
  font-variant-numeric: tabular-nums;
}

.metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 7px;
  overflow: hidden;
}

.tag {
  flex: none;
  padding: 1px 7px;
  border-radius: 5px;
  font-size: 10.5px;
  color: rgba(255, 236, 210, 0.82);
  background: rgba(255, 214, 160, 0.13);
  border: 1px solid rgba(255, 210, 160, 0.14);
}

.sales {
  flex: none;
  width: 68px;
  text-align: right;
  line-height: 1.1;
}

.sales b {
  display: block;
  font-size: 23px;
  font-weight: 900;
  letter-spacing: -0.6px;
  font-variant-numeric: tabular-nums;
  color: #ffd48e;
  text-shadow: 0 0 12px rgba(255, 160, 70, 0.42);
}

.sales b i {
  font-size: 12px;
  font-weight: 700;
  font-style: normal;
  letter-spacing: 0;
  margin-left: 1px;
  color: rgba(255, 214, 154, 0.86);
}

.sales em {
  display: block;
  margin-top: 1px;
  font-size: 10px;
  font-style: normal;
  color: rgba(255, 230, 205, 0.5);
}

.empty {
  padding: 24px 0;
  text-align: center;
  font-size: 12px;
  color: rgba(255, 232, 206, 0.45);
}

/* ---------- 窄容器降级（大屏缩窗 / 嵌入模式） ----------
   卡片内序号/缩略图/销量栏都是固定尺寸，列宽不足时会把名字挤没、
   把标签挤出卡片右边界压到隔壁面板；按列宽分级收缩消除穿模。 */

/* < 420px：缩小序号和缩略图，标签允许换行撑高卡片 */
@container (max-width: 420px) {
  .no {
    width: 30px;
    font-size: 22px;
  }

  .card:nth-child(1) .no {
    width: 36px;
    font-size: 26px;
    letter-spacing: -1px;
  }

  .thumb {
    width: 46px;
    height: 46px;
    font-size: 18px;
  }

  .line1 b {
    font-size: 14px;
  }

  .score {
    font-size: 22px;
  }

  .metrics {
    gap: 4px;
  }

  .tag {
    padding: 1px 5px;
    font-size: 10px;
  }

  .sales {
    width: 52px;
  }

  .sales b {
    font-size: 19px;
  }
}

/* < 340px：再缩一号。此档下销量栏+星标会占满整行，热菜卡藏星标
   （好评率条已表达质量），宝藏卡综合分换行显示；标签藏起「卫」 */
@container (max-width: 340px) {
  .card {
    gap: 6px;
    padding: 6px 8px 6px 6px;
  }

  .no {
    width: 26px;
    font-size: 19px;
  }

  .card:nth-child(1) .no {
    width: 30px;
    font-size: 22px;
    letter-spacing: -1px;
  }

  .thumb {
    width: 40px;
    height: 40px;
    font-size: 16px;
  }

  .line1 {
    flex-wrap: wrap;
    row-gap: 2px;
  }

  .line1 b {
    font-size: 13px;
  }

  .star {
    font-size: 13px;
  }

  .card.hot .star {
    display: none;
  }

  .score {
    font-size: 18px;
  }

  .metrics {
    gap: 4px;
    margin-top: 5px;
  }

  .tag {
    padding: 1px 4px;
    font-size: 9.5px;
  }

  .tag:nth-child(n + 3) {
    display: none;
  }

  .sales {
    width: 44px;
  }

  .sales b {
    font-size: 17px;
  }

  .rate {
    margin-top: 5px;
  }

  .rate em {
    font-size: 10px;
  }

  .rate em b {
    font-size: 13px;
  }
}

/* < 190px：极端窄列（小窗嵌入），只保名字 + 综合分/好评率 */
@container (max-width: 190px) {
  .metrics {
    display: none;
  }

  .sales {
    display: none;
  }
}
</style>
