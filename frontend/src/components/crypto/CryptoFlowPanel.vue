<template>
  <div class="cfp">
    <div class="cfp-head">
      <div>
        <h4>PTMOC 怎么跑</h4>
        <p class="cfp-tagline">不传原始轨迹，只在密文上算「偏没偏」</p>
      </div>
      <span class="cfp-mode" :class="mode">{{ modeLabel }}</span>
    </div>

    <!-- 一句话总览：评委第一眼就能抓住主线 -->
    <div class="cfp-story">
      <div
        v-for="(node, i) in storyNodes"
        :key="node.id"
        class="cfp-story-node"
        :class="{
          active: node.stages.includes(currentStage),
          done: isStoryDone(node),
          upcoming: !node.stages.includes(currentStage) && !isStoryDone(node)
        }"
      >
        <div class="cfp-story-num">{{ i + 1 }}</div>
        <div class="cfp-story-text">
          <strong>{{ node.title }}</strong>
          <span>{{ node.hint }}</span>
        </div>
        <div v-if="i < storyNodes.length - 1" class="cfp-story-arrow" aria-hidden="true">→</div>
      </div>
    </div>

    <div v-if="mode === 'loading'" class="cfp-loading">正在调用真实 PTMOC：Setup → Encrypt → Eval → Decrypt…</div>

    <!-- 当前步骤：白话解说 -->
    <div class="cfp-now" v-if="currentEvent && mode !== 'loading'">
      <div class="cfp-now-eyebrow">
        <span class="cfp-now-dot"></span>
        正在执行 · {{ stagePlainLabel }}
      </div>
      <div class="cfp-now-title">{{ plainTitle }}</div>
      <div class="cfp-now-desc">{{ plainDesc }}</div>
      <div class="cfp-now-meta" v-if="actorLine">
        <span class="who">{{ actorLine }}</span>
        <span class="sep" v-if="privacyLine">·</span>
        <span class="safe" v-if="privacyLine">{{ privacyLine }}</span>
      </div>
    </div>

    <!-- 谁看见什么：隐私直觉 -->
    <div class="cfp-who">
      <div
        v-for="lane in lanes"
        :key="lane.id"
        class="cfp-who-card"
        :class="{
          active: activeActors.has(lane.id),
          dim: activeActors.size && !activeActors.has(lane.id)
        }"
      >
        <div class="cfp-who-name">{{ lane.short }}</div>
        <div class="cfp-who-role">{{ lane.role }}</div>
        <div class="cfp-who-see" :class="visibilityTone(lane.id)">
          {{ visibilityText(lane.id) }}
        </div>
        <div class="cfp-who-action" v-if="laneCards[lane.id]">
          {{ laneCards[lane.id].title }}
        </div>
      </div>
    </div>

    <!-- 数据飞包（简化） -->
    <div class="cfp-flow" v-if="packet">
      <div class="cfp-flow-track">
        <span
          v-for="lane in lanes"
          :key="lane.id"
          class="cfp-flow-stop"
          :class="{ on: packet.fromId === lane.id || packet.toId === lane.id }"
        >{{ lane.short }}</span>
      </div>
      <div class="cfp-packet" :style="packetStyle">🔒 {{ packet.label }}</div>
    </div>

    <!-- 关键数字：少而精 -->
    <div class="cfp-facts" v-if="highlightFacts.length">
      <div class="cfp-fact" v-for="(f, i) in highlightFacts" :key="i">
        <span class="lab">{{ f.label }}</span>
        <span class="val">{{ f.value }}</span>
      </div>
    </div>

    <!-- 最终结果 -->
    <div class="cfp-result" v-if="resultBadge">
      <div class="cfp-result-badge" :class="resultTone">{{ resultBadge }}</div>
      <div class="cfp-result-meta" v-if="resultMeta">{{ resultMeta }}</div>
      <div class="cfp-privacy">✓ 原始 GPS 轨迹从未发给 Server / CSP，只交出「偏没偏」的答案</div>
    </div>

    <div class="cfp-time" v-if="totalDuration != null && totalDuration !== ''">
      总耗时 {{ totalDuration }} ms · 共 {{ processTrace.length }} 步重放
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps({
  mode: { type: String, default: 'offline' }, // online | offline | loading
  summary: { type: String, default: '' },
  processTrace: { type: Array, default: () => [] },
  totalDuration: { type: [Number, String], default: null },
  autoPlay: { type: Boolean, default: true },
  stepMs: { type: Number, default: 1100 }
})

const emit = defineEmits(['complete'])

const lanes = [
  { id: 'SENDER', short: '车辆', role: '数据拥有者', title: 'Sender' },
  { id: 'SERVER', short: '雾节点', role: '密文计算', title: 'Server' },
  { id: 'CSP', short: '云端', role: '协同掩码', title: 'CSP' },
  { id: 'RECEIVER', short: '验证方', role: '只拿结果', title: 'Receiver' }
]

/** 评委一眼能懂的 5 段故事线（映射到底层 stage） */
const storyNodes = [
  { id: 'boot', title: '准备', hint: '参数与密钥', stages: ['SETUP', 'KEYGEN'] },
  { id: 'encode', title: '编码', hint: '偏差 → x₁…x₄', stages: ['ENCODE'] },
  { id: 'encrypt', title: '加密发出', hint: '分片密文', stages: ['ENCRYPT'] },
  { id: 'eval', title: '密文计算', hint: '云端算函数', stages: ['EVAL'] },
  { id: 'finish', title: '解密结论', hint: '只得验证值', stages: ['DECRYPT', 'RESULT'] }
]

const stagePlain = {
  SETUP: '系统准备',
  KEYGEN: '生成密钥',
  ENCODE: '轨迹编码',
  ENCRYPT: '加密与分发',
  EVAL: '密文上运算',
  DECRYPT: '解密验证值',
  RESULT: '给出结论'
}

/** 每类事件的白话标题/说明（优先用它，而不是后端技术文案） */
const plainCopy = {
  SETUP: {
    title: '先搭好密码「工作台」',
    desc: '生成公共参数（安全参数、RSA 等），发给参与各方。大家用同一套规则，但还看不到任何轨迹。'
  },
  KEYGEN: {
    title: '各方各自拿好钥匙',
    desc: '车辆、雾节点、云端、验证方各自生成密钥材料。私钥不互相公开，后面才能安全协作。'
  },
  ENCODE: {
    title: '把「偏没偏」压成四个数',
    desc: '车辆本地把行程相对参考路线的偏差，编码成 x₁～x₄（空间/时间的平均与最大偏差）。原始 GPS 点仍留在车端。'
  },
  ENCRYPT: {
    title: '切成碎片再上锁发出去',
    desc: '用门限秘密共享把数据分片并加密：雾节点和云端各自只拿到密文份额，拼不齐也解不开明文轨迹。'
  },
  EVAL: {
    title: '在「看不懂」的数据上算答案',
    desc: '雾节点做密文函数评估，云端加随机掩码协同。两边都不看见原始轨迹，却能算出验证函数的密文结果。'
  },
  DECRYPT: {
    title: '验证方只解开「答案」',
    desc: '验证方解密得到的是函数评估值（偏没偏），不是原始 GPS 轨迹。明文轨迹自始至终没离开车辆。'
  },
  RESULT: {
    title: '输出最终判定',
    desc: '根据解密值与门限，给出通过 / 时空异常等结论，并附综合评分。'
  }
}

const modeLabel = computed(() => {
  if (props.mode === 'online') return '真实后端重放'
  if (props.mode === 'loading') return '调用后端中'
  return '离线演示'
})

const playIndex = ref(-1)
const currentEvent = ref(null)
const currentStage = ref('')
const completedStages = ref(new Set())
const activeActors = ref(new Set())
const laneCards = ref({})
const packet = ref(null)
const packetStyle = ref({})
const resultBadge = ref('')
const resultMeta = ref('')
const resultTone = ref('fail')
/** 各角色当前「看见什么」 */
const visibility = ref({
  SENDER: '持有明文',
  SERVER: '尚未参与',
  CSP: '尚未参与',
  RECEIVER: '尚未参与'
})

let timer = null
let cancelled = false

const stagePlainLabel = computed(() => stagePlain[currentStage.value] || currentStage.value || '—')

const plainTitle = computed(() => {
  const stage = currentStage.value
  const fallback = currentEvent.value?.title || ''
  return plainCopy[stage]?.title || fallback
})

const plainDesc = computed(() => {
  const stage = currentStage.value
  const ev = currentEvent.value
  // Encode 时若有具体 x 值，补一句更直观
  if (stage === 'ENCODE' && ev?.displayData) {
    const d = ev.displayData
    if (d.x1 != null && d.x1 !== '—') {
      return `本趟编码结果：平均空间偏差 ${d.x1}，最大 ${d.x2}；平均时间偏差 ${d.x3}，最大 ${d.x4}。原始点仍在车端。`
    }
  }
  if (stage === 'DECRYPT' && ev?.displayData?.decryptedResult != null) {
    return `解开的验证值 f(...) = ${ev.displayData.decryptedResult}。这是「偏没偏」的答案，不是轨迹坐标。`
  }
  if (stage === 'RESULT' && ev?.displayData?.verificationStatus) {
    return `结论：${ev.displayData.verificationStatus}${ev.displayData.score != null ? `，评分 ${ev.displayData.score}` : ''}。`
  }
  return plainCopy[stage]?.desc || ev?.description || ''
})

const actorLine = computed(() => {
  const ev = currentEvent.value
  if (!ev) return ''
  if (ev.actor === 'SYSTEM') return '参与方：全体'
  const name = (id) => lanes.find((l) => l.id === id)?.short || id
  if (ev.actor && ev.target) return `${name(ev.actor)} → ${name(ev.target)}`
  if (ev.actor) return `执行方：${name(ev.actor)}`
  return ''
})

const privacyLine = computed(() => {
  const stage = currentStage.value
  if (stage === 'ENCODE') return '明文仅在车辆'
  if (stage === 'ENCRYPT') return '发出去的都是密文'
  if (stage === 'EVAL') return 'Server/CSP 不见轨迹'
  if (stage === 'DECRYPT' || stage === 'RESULT') return '只恢复验证值'
  return ''
})

const highlightFacts = computed(() => {
  const d = currentEvent.value?.displayData
  if (!d || typeof d !== 'object') return []
  const rows = []
  const prefer = [
    ['x1', d.x1Label || '平均空间偏差'],
    ['x2', d.x2Label || '最大空间偏差'],
    ['x3', d.x3Label || '平均时间偏差'],
    ['x4', d.x4Label || '最大时间偏差'],
    ['thresholdK', '门限 k'],
    ['function', '验证函数'],
    ['decryptedResult', '解密结果 f(...)'],
    ['payload', '传输内容'],
    ['result', '交接内容'],
    ['score', '评分'],
    ['verificationStatus', '验证状态']
  ]
  for (const [key, label] of prefer) {
    if (d[key] != null && d[key] !== '') {
      rows.push({ label, value: String(d[key]) })
    }
  }
  return rows.slice(0, 4)
})

function isStoryDone(node) {
  // 当前正停在这一段时不算 done，避免和 active 抢样式
  if (node.stages.includes(currentStage.value)) return false
  const order = ['SETUP', 'KEYGEN', 'ENCODE', 'ENCRYPT', 'EVAL', 'DECRYPT', 'RESULT']
  const cur = currentStage.value
  if (!cur) return false
  const curIdx = order.indexOf(cur)
  const lastStage = node.stages[node.stages.length - 1]
  const lastIdx = order.indexOf(lastStage)
  if (completedStages.value.has(lastStage)) return true
  return curIdx > lastIdx
}

function visibilityText(id) {
  return visibility.value[id] || '—'
}

function visibilityTone(id) {
  const t = visibility.value[id] || ''
  if (t.includes('明文')) return 'plain'
  if (t.includes('密文') || t.includes('不见') || t.includes('不可见')) return 'cipher'
  if (t.includes('验证') || t.includes('结论') || t.includes('结果')) return 'ok'
  return 'idle'
}

watch(
  () => [props.processTrace, props.mode, props.autoPlay],
  () => {
    resetPlayback()
    if (props.mode === 'loading') return
    if (props.autoPlay && props.processTrace?.length) {
      startPlayback()
    }
  },
  { immediate: true, deep: true }
)

onBeforeUnmount(() => {
  cancelled = true
  clearTimer()
})

function resetPlayback() {
  clearTimer()
  cancelled = false
  playIndex.value = -1
  currentEvent.value = null
  currentStage.value = ''
  completedStages.value = new Set()
  activeActors.value = new Set()
  laneCards.value = {}
  packet.value = null
  resultBadge.value = ''
  resultMeta.value = ''
  visibility.value = {
    SENDER: '持有明文',
    SERVER: '尚未参与',
    CSP: '尚未参与',
    RECEIVER: '尚未参与'
  }
}

function clearTimer() {
  if (timer) {
    clearTimeout(timer)
    timer = null
  }
}

function startPlayback() {
  playIndex.value = -1
  playNext()
}

function playNext() {
  if (cancelled) return
  const next = playIndex.value + 1
  if (next >= props.processTrace.length) {
    emit('complete')
    return
  }
  playIndex.value = next
  applyEvent(props.processTrace[next])
  timer = setTimeout(playNext, props.stepMs)
}

function applyEvent(ev) {
  currentEvent.value = ev
  const stage = ev.stage || ''
  if (currentStage.value && currentStage.value !== stage) {
    const nextSet = new Set(completedStages.value)
    nextSet.add(currentStage.value)
    completedStages.value = nextSet
  }
  currentStage.value = stage

  const actors = new Set()
  if (ev.actor && ev.actor !== 'SYSTEM') actors.add(ev.actor)
  if (ev.target) actors.add(ev.target)
  if (ev.actor === 'SYSTEM') {
    lanes.forEach((l) => actors.add(l.id))
  }
  activeActors.value = actors

  // 更新「谁看见什么」
  updateVisibility(ev, stage)

  // 泳道动作摘要
  const cards = { ...laneCards.value }
  if (ev.actor === 'SYSTEM') {
    lanes.forEach((l) => {
      cards[l.id] = { title: stage === 'SETUP' ? '收到公共参数' : '持有本方密钥' }
    })
  } else if (ev.actor) {
    cards[ev.actor] = { title: shortAction(ev) }
  }
  if (ev.target) {
    cards[ev.target] = {
      title: ev.displayData?.payload
        ? `收到 ${friendlyPayload(ev.displayData.payload)}`
        : ev.displayData?.result
          ? `收到 ${friendlyPayload(ev.displayData.result)}`
          : '收到密文'
    }
  }
  laneCards.value = cards

  if (ev.target && ev.actor && ev.actor !== 'SYSTEM') {
    animatePacket(ev.actor, ev.target, friendlyPayload(ev.displayData?.payload || ev.displayData?.result || '密文'))
  } else {
    packet.value = null
  }

  if (stage === 'RESULT') {
    const badge = ev.displayData?.resultBadge || ev.displayData?.verificationStatus || 'RESULT'
    resultBadge.value = String(badge)
    const score = ev.displayData?.score
    const status = ev.displayData?.verificationStatus
    resultMeta.value = [status, score != null ? `评分 ${score}` : null].filter(Boolean).join(' · ')
    resultTone.value = String(badge).includes('PASS') || String(status || '').includes('通过') ? 'pass' : 'fail'
    const done = new Set(completedStages.value)
    done.add('RESULT')
    completedStages.value = done
    visibility.value = {
      ...visibility.value,
      RECEIVER: '已得验证结论',
      SERVER: '只见过密文',
      CSP: '只见过密文',
      SENDER: '明文未外传'
    }
  }
}

function shortAction(ev) {
  const stage = ev.stage
  if (stage === 'ENCODE') return '本地编码偏差'
  if (stage === 'ENCRYPT') return ev.target ? '发送密文' : '分片并加密'
  if (stage === 'EVAL') return ev.actor === 'CSP' ? '加随机掩码' : '密文函数评估'
  if (stage === 'DECRYPT') return '解密验证值'
  if (stage === 'RESULT') return '输出结论'
  return ev.title || '处理中'
}

function friendlyPayload(raw) {
  const s = String(raw || '')
  if (/share/i.test(s)) return '加密份额'
  if (/aux/i.test(s)) return '辅助密文'
  if (/function|result/i.test(s)) return '密文结果'
  if (/cipher|encrypt/i.test(s)) return '密文'
  return s.length > 16 ? '密文数据包' : s
}

function updateVisibility(ev, stage) {
  const v = { ...visibility.value }
  if (stage === 'SETUP' || stage === 'KEYGEN') {
    v.SENDER = '持有明文'
    v.SERVER = '仅有公共参数'
    v.CSP = '仅有公共参数'
    v.RECEIVER = '仅有公共参数'
  } else if (stage === 'ENCODE') {
    v.SENDER = '明文 → 四个偏差数'
    v.SERVER = '仍不可见'
    v.CSP = '仍不可见'
    v.RECEIVER = '仍不可见'
  } else if (stage === 'ENCRYPT') {
    v.SENDER = '明文未离开'
    if (ev.target === 'SERVER') v.SERVER = '只见加密份额'
    if (ev.target === 'CSP') v.CSP = '只见辅助密文'
    if (!ev.target) {
      v.SERVER = v.SERVER.includes('份额') ? v.SERVER : '等待密文'
      v.CSP = v.CSP.includes('密文') ? v.CSP : '等待密文'
    }
    v.RECEIVER = '仍不可见'
  } else if (stage === 'EVAL') {
    v.SENDER = '明文未外传'
    v.SERVER = '密文上算函数'
    v.CSP = '密文协同掩码'
    if (ev.target === 'RECEIVER') v.RECEIVER = '收到密文结果'
    else v.RECEIVER = '等待密文结果'
  } else if (stage === 'DECRYPT') {
    v.SENDER = '明文未外传'
    v.SERVER = '不见轨迹'
    v.CSP = '不见轨迹'
    v.RECEIVER = '解出验证值'
  }
  visibility.value = v
}

function animatePacket(fromId, toId, label) {
  packet.value = { fromId, toId, label }
  const idx = { SENDER: 0, SERVER: 1, CSP: 2, RECEIVER: 3 }
  const from = (idx[fromId] ?? 0) / 3
  const to = (idx[toId] ?? 3) / 3
  packetStyle.value = {
    left: `calc(${from * 100}% + 12.5%)`,
    opacity: '1',
    transition: 'none'
  }
  requestAnimationFrame(() => {
    requestAnimationFrame(() => {
      packetStyle.value = {
        left: `calc(${to * 100}% + 12.5%)`,
        opacity: '1',
        transition: `left ${Math.max(450, props.stepMs - 250)}ms ease`
      }
    })
  })
}
</script>

<style scoped>
.cfp {
  background: #f7f9fc;
  border: 1px solid #e8eef5;
  border-radius: 10px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.cfp-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}
.cfp-head h4 {
  margin: 0;
  font-size: 15px;
  color: #1a1a2e;
  padding-bottom: 0;
  border: none;
}
.cfp-tagline {
  margin: 3px 0 0;
  font-size: 11px;
  color: #8c8c8c;
  line-height: 1.4;
}
.cfp-mode {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 4px;
  white-space: nowrap;
  flex-shrink: 0;
}
.cfp-mode.online { background: #f6ffed; color: #389e0d; border: 1px solid #b7eb8f; }
.cfp-mode.offline { background: #fff7e6; color: #d46b08; border: 1px solid #ffd591; }
.cfp-mode.loading { background: #e6f4ff; color: #1677ff; border: 1px solid #91caff; }
.cfp-loading { font-size: 13px; color: #1677ff; }

/* 故事线总览 */
.cfp-story {
  display: flex;
  align-items: stretch;
  gap: 0;
  background: #fff;
  border: 1px solid #e8eef5;
  border-radius: 8px;
  padding: 8px 6px;
  overflow-x: auto;
}
.cfp-story-node {
  position: relative;
  flex: 1;
  min-width: 72px;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 4px;
  opacity: 0.45;
  transition: opacity 0.25s, transform 0.25s;
}
.cfp-story-node.active {
  opacity: 1;
  transform: translateY(-1px);
}
.cfp-story-node.done { opacity: 0.85; }
.cfp-story-num {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #f0f0f0;
  color: #8c8c8c;
  font-size: 11px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
.cfp-story-node.active .cfp-story-num {
  background: #1890ff;
  color: #fff;
  box-shadow: 0 0 0 3px rgba(24, 144, 255, 0.2);
}
.cfp-story-node.done .cfp-story-num {
  background: #52c41a;
  color: #fff;
}
.cfp-story-text {
  display: flex;
  flex-direction: column;
  gap: 1px;
  padding: 0 2px;
}
.cfp-story-text strong {
  font-size: 11px;
  color: #1a1a2e;
  font-weight: 700;
}
.cfp-story-text span {
  font-size: 10px;
  color: #8c8c8c;
  line-height: 1.25;
}
.cfp-story-arrow {
  position: absolute;
  right: -6px;
  top: 4px;
  font-size: 11px;
  color: #bfbfbf;
  z-index: 1;
}

/* 当前白话解说 */
.cfp-now {
  background: linear-gradient(135deg, #e6f4ff 0%, #f0f5ff 100%);
  border: 1px solid #91caff;
  border-radius: 8px;
  padding: 10px 12px;
}
.cfp-now-eyebrow {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: #1677ff;
  font-weight: 600;
  margin-bottom: 4px;
}
.cfp-now-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #1677ff;
  animation: cfp-pulse 1.2s ease infinite;
}
@keyframes cfp-pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.45; transform: scale(0.85); }
}
.cfp-now-title {
  font-size: 14px;
  font-weight: 700;
  color: #1a1a2e;
  line-height: 1.35;
}
.cfp-now-desc {
  margin-top: 4px;
  font-size: 12px;
  color: #434343;
  line-height: 1.55;
}
.cfp-now-meta {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 4px 6px;
  font-size: 11px;
}
.cfp-now-meta .who {
  background: #fff;
  border: 1px solid #d6e4ff;
  color: #1d39c4;
  padding: 2px 7px;
  border-radius: 4px;
  font-weight: 600;
}
.cfp-now-meta .safe {
  background: #f6ffed;
  border: 1px solid #b7eb8f;
  color: #389e0d;
  padding: 2px 7px;
  border-radius: 4px;
  font-weight: 600;
}
.cfp-now-meta .sep { color: #bfbfbf; }

/* 谁看见什么 */
.cfp-who {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 6px;
}
.cfp-who-card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 8px 6px;
  text-align: center;
  transition: border-color 0.25s, box-shadow 0.25s, opacity 0.25s;
  min-width: 0;
}
.cfp-who-card.active {
  border-color: #1890ff;
  box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.15);
}
.cfp-who-card.dim { opacity: 0.4; }
.cfp-who-name { font-size: 12px; font-weight: 700; color: #1a1a2e; }
.cfp-who-role { font-size: 10px; color: #8c8c8c; margin: 2px 0 6px; }
.cfp-who-see {
  font-size: 10px;
  font-weight: 600;
  padding: 3px 4px;
  border-radius: 4px;
  line-height: 1.3;
  word-break: break-word;
}
.cfp-who-see.plain { background: #f6ffed; color: #389e0d; }
.cfp-who-see.cipher { background: #fff1f0; color: #cf1322; }
.cfp-who-see.ok { background: #e6fffb; color: #08979c; }
.cfp-who-see.idle { background: #f5f5f5; color: #8c8c8c; }
.cfp-who-action {
  margin-top: 5px;
  font-size: 10px;
  color: #595959;
  line-height: 1.3;
  word-break: break-word;
}

/* 飞包轨道 */
.cfp-flow {
  position: relative;
  height: 36px;
  background: #fff;
  border: 1px dashed #d9d9d9;
  border-radius: 8px;
  overflow: hidden;
}
.cfp-flow-track {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  height: 100%;
  align-items: center;
}
.cfp-flow-stop {
  text-align: center;
  font-size: 10px;
  color: #bfbfbf;
}
.cfp-flow-stop.on { color: #1890ff; font-weight: 700; }
.cfp-packet {
  position: absolute;
  top: 50%;
  z-index: 5;
  transform: translate(-50%, -50%);
  background: #1a1a2e;
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  padding: 3px 8px;
  border-radius: 999px;
  white-space: nowrap;
  pointer-events: none;
  box-shadow: 0 4px 12px rgba(0,0,0,0.25);
}

/* 关键事实 */
.cfp-facts {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px 8px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 8px 10px;
}
.cfp-fact {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}
.cfp-fact .lab { font-size: 10px; color: #8c8c8c; }
.cfp-fact .val {
  font-size: 12px;
  color: #262626;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  word-break: break-all;
}

.cfp-result {
  text-align: center;
  padding: 12px 10px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #f0f0f0;
}
.cfp-result-badge {
  font-size: 20px;
  font-weight: 800;
  letter-spacing: 0.5px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}
.cfp-result-badge.pass { color: #389e0d; }
.cfp-result-badge.fail { color: #cf1322; }
.cfp-result-meta { margin-top: 4px; font-size: 12px; color: #595959; }
.cfp-privacy {
  margin-top: 8px;
  font-size: 11px;
  color: #389e0d;
  background: #f6ffed;
  border-radius: 4px;
  padding: 6px 8px;
  line-height: 1.4;
}
.cfp-time { font-size: 11px; color: #8c8c8c; text-align: right; }
</style>
