<template>
  <div class="cfp">
    <div class="cfp-head">
      <h4>PTMOC 密码数据流</h4>
      <span class="cfp-mode" :class="mode">{{ modeLabel }}</span>
    </div>
    <div class="cfp-summary" v-if="summary">{{ summary }}</div>

    <!-- 阶段概览（次要） -->
    <div class="cfp-stages">
      <span
        v-for="s in stageList"
        :key="s.id"
        class="cfp-stage-pill"
        :class="{ active: s.id === currentStage, done: completedStages.has(s.id) }"
      >
        {{ s.label }}
        <i v-if="completedStages.has(s.id)">✓</i>
      </span>
    </div>

    <div v-if="mode === 'loading'" class="cfp-loading">正在执行真实 PTMOC Setup → Decrypt...</div>

    <!-- 四泳道 -->
    <div class="cfp-lanes" ref="lanesRef">
      <div
        v-for="lane in lanes"
        :key="lane.id"
        class="cfp-lane"
        :class="{ active: activeActors.has(lane.id), dim: activeActors.size && !activeActors.has(lane.id) }"
      >
        <div class="cfp-lane-title">{{ lane.title }}</div>
        <div class="cfp-lane-role">{{ lane.role }}</div>
        <div class="cfp-lane-body">
          <div v-if="laneCards[lane.id]" class="cfp-card" :class="laneCards[lane.id].tone">
            <div class="cfp-card-title">{{ laneCards[lane.id].title }}</div>
            <div class="cfp-card-desc">{{ laneCards[lane.id].desc }}</div>
            <ul v-if="laneCards[lane.id].bullets?.length" class="cfp-bullets">
              <li v-for="(b, i) in laneCards[lane.id].bullets" :key="i">{{ b }}</li>
            </ul>
          </div>
          <div v-else class="cfp-lane-empty">等待数据…</div>
        </div>
      </div>

      <!-- 飞行动画包 -->
      <div
        v-if="packet"
        class="cfp-packet"
        :style="packetStyle"
      >
        🔒 {{ packet.label }}
      </div>
    </div>

    <!-- 当前事件详情 -->
    <div class="cfp-detail" v-if="currentEvent">
      <div class="cfp-detail-title">{{ currentEvent.title }}</div>
      <div class="cfp-detail-desc">{{ currentEvent.description }}</div>
      <div class="cfp-detail-grid" v-if="detailRows.length">
        <div class="cfp-detail-row" v-for="(r, i) in detailRows" :key="i">
          <span class="k">{{ r.label }}</span>
          <span class="v">{{ r.value }}</span>
        </div>
      </div>
    </div>

    <!-- 最终结果 -->
    <div class="cfp-result" v-if="resultBadge">
      <div class="cfp-result-badge" :class="resultTone">{{ resultBadge }}</div>
      <div class="cfp-result-meta" v-if="resultMeta">{{ resultMeta }}</div>
      <div class="cfp-privacy">原始轨迹明文未发送给 Server / CSP</div>
    </div>

    <div class="cfp-time" v-if="totalDuration != null && totalDuration !== ''">
      总耗时 {{ totalDuration }} ms · Trace {{ processTrace.length }} 步
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
  stepMs: { type: Number, default: 900 }
})

const emit = defineEmits(['complete'])

const lanes = [
  { id: 'SENDER', title: 'Sender', role: '车辆 / 数据拥有者' },
  { id: 'SERVER', title: 'Server', role: '雾计算节点' },
  { id: 'CSP', title: 'CSP', role: '云服务节点' },
  { id: 'RECEIVER', title: 'Receiver', role: '验证方' }
]

const stageList = [
  { id: 'SETUP', label: 'Setup' },
  { id: 'KEYGEN', label: 'KeyGen' },
  { id: 'ENCODE', label: 'Encode' },
  { id: 'ENCRYPT', label: 'Encrypt' },
  { id: 'EVAL', label: 'Eval' },
  { id: 'DECRYPT', label: 'Decrypt' },
  { id: 'RESULT', label: 'Result' }
]

const modeLabel = computed(() => {
  if (props.mode === 'online') return '真实后端 · Trace 重放'
  if (props.mode === 'loading') return '正在调用后端...'
  return '离线演示兜底'
})

const lanesRef = ref(null)
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

let timer = null
let cancelled = false

const detailRows = computed(() => {
  const d = currentEvent.value?.displayData
  if (!d || typeof d !== 'object') return []
  const rows = []
  const prefer = [
    ['x1', d.x1Label || 'x₁'],
    ['x2', d.x2Label || 'x₂'],
    ['x3', d.x3Label || 'x₃'],
    ['x4', d.x4Label || 'x₄'],
    ['thresholdK', '门限 k'],
    ['function', '验证函数 f'],
    ['decryptedResult', '解密结果'],
    ['payload', '载荷'],
    ['result', '结果'],
    ['action', '动作'],
    ['domain', '计算域'],
    ['plaintext', '明文可见性'],
    ['privacyNote', '隐私说明'],
    ['anomalyDesc', '异常说明'],
    ['score', 'Score'],
    ['verificationStatus', '验证状态'],
    ['lambda', 'λ'],
    ['rsa', 'RSA'],
    ['entities', '实体'],
    ['secretHint', '密钥'],
    ['shareHint', '分片'],
    ['note', '说明'],
    ['error', '错误']
  ]
  for (const [key, label] of prefer) {
    if (d[key] != null && d[key] !== '') {
      rows.push({ label, value: String(d[key]) })
    }
  }
  return rows.slice(0, 8)
})

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

  // 更新泳道卡片
  const cards = { ...laneCards.value }
  const bulletFrom = buildBullets(ev)
  if (ev.actor === 'SYSTEM') {
    lanes.forEach((l) => {
      cards[l.id] = {
        title: ev.title,
        desc: stage === 'SETUP' ? '收到公共参数 PP' : stage === 'KEYGEN' ? '持有本方密钥材料' : ev.description,
        bullets: bulletFrom.slice(0, 2),
        tone: 'info'
      }
    })
  } else if (ev.actor) {
    cards[ev.actor] = {
      title: ev.title,
      desc: ev.description,
      bullets: bulletFrom,
      tone: toneForStage(stage)
    }
  }
  if (ev.target && ev.displayData?.payload) {
    cards[ev.target] = {
      title: '收到: ' + ev.displayData.payload,
      desc: '明文不可见 · 仅密文/辅助数据',
      bullets: ['plaintext: 不可见'],
      tone: 'cipher'
    }
  }
  if (ev.target && ev.displayData?.result) {
    cards[ev.target] = {
      title: '收到: ' + ev.displayData.result,
      desc: ev.description,
      bullets: ['待解密'],
      tone: 'cipher'
    }
  }
  laneCards.value = cards

  // 飞包
  if (ev.target && ev.actor && ev.actor !== 'SYSTEM') {
    animatePacket(ev.actor, ev.target, ev.displayData?.payload || ev.displayData?.result || '密文')
  } else {
    packet.value = null
  }

  if (stage === 'RESULT') {
    const badge = ev.displayData?.resultBadge || ev.displayData?.verificationStatus || 'RESULT'
    resultBadge.value = String(badge)
    const score = ev.displayData?.score
    const status = ev.displayData?.verificationStatus
    resultMeta.value = [status, score != null ? `Score: ${score}` : null].filter(Boolean).join(' · ')
    resultTone.value = String(badge).includes('PASS') || String(status || '').includes('通过') ? 'pass' : 'fail'
    const done = new Set(completedStages.value)
    done.add('RESULT')
    completedStages.value = done
  }
}

function buildBullets(ev) {
  const d = ev.displayData || {}
  const out = []
  if (d.x1 != null) out.push(`x₁=${d.x1}`)
  if (d.x2 != null) out.push(`x₂=${d.x2}`)
  if (d.shareHint) out.push(String(d.shareHint))
  if (d.action) out.push(String(d.action))
  if (d.decryptedResult != null && d.decryptedResult !== '') out.push(`f(...)=${d.decryptedResult}`)
  if (d.plaintext) out.push(`明文: ${d.plaintext}`)
  return out
}

function toneForStage(stage) {
  if (stage === 'ENCODE') return 'plain'
  if (stage === 'ENCRYPT' || stage === 'EVAL') return 'cipher'
  if (stage === 'DECRYPT' || stage === 'RESULT') return 'ok'
  return 'info'
}

function animatePacket(fromId, toId, label) {
  packet.value = { fromId, toId, label }
  // 简化：用 CSS 百分比位置近似四列中心
  const idx = { SENDER: 0, SERVER: 1, CSP: 2, RECEIVER: 3 }
  const from = (idx[fromId] ?? 0) / 3
  const to = (idx[toId] ?? 3) / 3
  packetStyle.value = {
    left: `calc(${from * 100}% + 8%)`,
    opacity: '1',
    transition: 'none'
  }
  requestAnimationFrame(() => {
    requestAnimationFrame(() => {
      packetStyle.value = {
        left: `calc(${to * 100}% + 8%)`,
        opacity: '1',
        transition: `left ${Math.max(400, props.stepMs - 200)}ms ease`
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
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.cfp-head h4 {
  margin: 0;
  font-size: 14px;
  color: #1a1a2e;
  padding-bottom: 0;
  border: none;
}
.cfp-mode {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 4px;
}
.cfp-mode.online { background: #f6ffed; color: #389e0d; border: 1px solid #b7eb8f; }
.cfp-mode.offline { background: #fff7e6; color: #d46b08; border: 1px solid #ffd591; }
.cfp-mode.loading { background: #e6f4ff; color: #1677ff; border: 1px solid #91caff; }
.cfp-summary { font-size: 11px; color: #666; word-break: break-all; }
.cfp-loading { font-size: 13px; color: #1677ff; }

.cfp-stages {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.cfp-stage-pill {
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 999px;
  background: #fff;
  border: 1px solid #d9d9d9;
  color: #888;
}
.cfp-stage-pill.active {
  border-color: #1890ff;
  color: #1890ff;
  background: #e6f4ff;
  font-weight: 600;
}
.cfp-stage-pill.done {
  border-color: #b7eb8f;
  color: #389e0d;
  background: #f6ffed;
}

.cfp-lanes {
  position: relative;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 6px;
  min-height: 160px;
}
.cfp-lane {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 8px;
  transition: border-color 0.25s, box-shadow 0.25s, opacity 0.25s;
  min-width: 0;
}
.cfp-lane.active {
  border-color: #1890ff;
  box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.15);
}
.cfp-lane.dim { opacity: 0.45; }
.cfp-lane-title { font-size: 12px; font-weight: 700; color: #1a1a2e; }
.cfp-lane-role { font-size: 10px; color: #8c8c8c; margin-bottom: 6px; }
.cfp-lane-body { min-height: 88px; }
.cfp-lane-empty { font-size: 11px; color: #bbb; padding-top: 20px; text-align: center; }

.cfp-card {
  border-radius: 6px;
  padding: 6px 7px;
  font-size: 11px;
  line-height: 1.35;
}
.cfp-card.info { background: #f0f5ff; color: #1d39c4; }
.cfp-card.plain { background: #f6ffed; color: #389e0d; }
.cfp-card.cipher { background: #fff1f0; color: #cf1322; }
.cfp-card.ok { background: #e6fffb; color: #08979c; }
.cfp-card-title { font-weight: 700; margin-bottom: 2px; }
.cfp-card-desc { opacity: 0.9; word-break: break-word; }
.cfp-bullets { margin: 4px 0 0; padding-left: 14px; }
.cfp-bullets li { margin: 0; }

.cfp-packet {
  position: absolute;
  top: 42%;
  z-index: 5;
  transform: translateX(-50%);
  background: #1a1a2e;
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  padding: 4px 8px;
  border-radius: 999px;
  white-space: nowrap;
  pointer-events: none;
  box-shadow: 0 4px 12px rgba(0,0,0,0.25);
}

.cfp-detail {
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  padding: 8px 10px;
}
.cfp-detail-title { font-size: 13px; font-weight: 700; color: #1a1a2e; }
.cfp-detail-desc { font-size: 12px; color: #595959; margin-top: 2px; }
.cfp-detail-grid { margin-top: 6px; display: flex; flex-direction: column; gap: 3px; }
.cfp-detail-row { display: flex; gap: 8px; font-size: 11px; }
.cfp-detail-row .k { color: #8c8c8c; min-width: 88px; flex-shrink: 0; }
.cfp-detail-row .v { color: #262626; word-break: break-all; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }

.cfp-result {
  text-align: center;
  padding: 10px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #f0f0f0;
}
.cfp-result-badge {
  font-size: 18px;
  font-weight: 800;
  letter-spacing: 0.5px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}
.cfp-result-badge.pass { color: #389e0d; }
.cfp-result-badge.fail { color: #cf1322; }
.cfp-result-meta { margin-top: 4px; font-size: 12px; color: #595959; }
.cfp-privacy { margin-top: 6px; font-size: 11px; color: #8c8c8c; }
.cfp-time { font-size: 11px; color: #8c8c8c; text-align: right; }
</style>
