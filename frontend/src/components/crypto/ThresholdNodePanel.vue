<template>
  <section class="threshold-panel" aria-label="多服务器门限演示">
    <div class="threshold-heading"><strong>多服务器门限协作</strong><span>k = {{ thresholdK }} / n = 5</span></div>
    <p>至少 {{ thresholdK }} 组服务器与 CSP 协作才能恢复结果。k 是密码学门限，由系统配置。</p>
    <div class="node-grid">
      <button v-for="id in SERVER_COUNT" :key="id" type="button" :disabled="locked"
        :aria-label="`节点 ${id}，${onlineIds.includes(id) ? '在线' : '离线'}，点击切换`"
        :aria-pressed="onlineIds.includes(id)" :class="{ offline: !onlineIds.includes(id), selected: quorum.ready && quorum.selected.includes(id) }"
        @click="toggle(id)">
        <strong>Ser {{ id }}</strong><span>↕ CSP {{ id }}</span>
        <small>{{ !onlineIds.includes(id) ? '离线' : quorum.ready && quorum.selected.includes(id) ? '入选协作组' : '在线待命' }}</small>
      </button>
    </div>
    <div class="quorum-status" :class="{ blocked: !quorum.ready }" role="status">
      在线 {{ quorum.online.length }} / 5 · {{ quorum.ready ? `已满足 ${thresholdK} 组协作要求` : `不足 ${thresholdK} 组，禁止验证与恢复` }}
    </div>
    <div class="node-presets">
      <button type="button" :disabled="locked" @click="$emit('update:onlineIds', [1,2,3,4,5])">全部恢复</button>
      <button type="button" :disabled="locked" @click="$emit('update:onlineIds', Array.from({length: thresholdK}, (_, i) => i + 1))">恰好 k 组</button>
      <button type="button" :disabled="locked" @click="$emit('update:onlineIds', Array.from({length: thresholdK - 1}, (_, i) => i + 1))">不足 k 组</button>
    </div>
    <details v-if="configurable" class="crypto-settings">
      <summary>密码学配置（演示管理）</summary>
      <label>恢复门限 k <select :value="thresholdK" :disabled="locked" @change="$emit('update:thresholdK', Number($event.target.value))">
        <option v-for="k in [2,3,4,5]" :key="k" :value="k">{{ k }} / 5</option>
      </select></label>
      <p>普通行程无需选择 k。调整参数后，下一次验证使用新配置。</p>
    </details>
    <small class="node-note">单机逻辑节点与故障演示；在线状态由演示控制，非独立主机探测。接口也会检查协作数量。</small>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { getQuorum, SERVER_COUNT } from '../../utils/demoPolicy.js'
const props = defineProps({ thresholdK: { type: Number, default: 3 }, onlineIds: { type: Array, required: true }, locked: Boolean, configurable: Boolean })
const emit = defineEmits(['update:onlineIds', 'update:thresholdK'])
const quorum = computed(() => getQuorum(props.onlineIds, props.thresholdK))
function toggle(id) { emit('update:onlineIds', props.onlineIds.includes(id) ? props.onlineIds.filter(n => n !== id) : [...props.onlineIds, id]) }
</script>

<style scoped>
.threshold-panel { background: #f8fafc; border: 1px solid #dce5ef; border-radius: 8px; padding: 14px; color: #24364b; }
.threshold-heading { display: flex; justify-content: space-between; gap: 12px; font-size: 14px; }
.threshold-heading span { font-family: monospace; color: #1969a8; }
p { font-size: 12px; line-height: 1.65; margin: 8px 0 12px; color: #53647a; }
.node-grid { display: grid; grid-template-columns: repeat(5,minmax(0,1fr)); gap: 6px; }
.node-grid button { display: flex; flex-direction: column; gap: 5px; align-items: center; padding: 10px 2px; border: 1px solid #bbd1e9; border-radius: 6px; background: #fff; color: #28597e; cursor: pointer; }
.node-grid button.selected { background: #e8f6ef; border-color: #69b68a; color: #216344; }
.node-grid button.offline { background: #f2f2f2; border-color: #ccc; color: #777; }
.node-grid span, .node-grid small { font-size: 10px; }
.quorum-status { margin-top: 10px; font-size: 12px; color: #23734b; font-weight: 600; }
.quorum-status.blocked { color: #af3030; }
.node-presets { display: flex; gap: 6px; margin-top: 10px; flex-wrap: wrap; }
.node-presets button { background: #fff; border: 1px solid #c6d3e1; padding: 5px 8px; border-radius: 4px; color: #345471; cursor: pointer; }
button:disabled { cursor: not-allowed; opacity: .55; }
.crypto-settings { margin-top: 12px; font-size: 12px; }
summary { cursor: pointer; }
.crypto-settings label { display: block; margin-top: 12px; }
.node-note { display: block; font-size: 10px; color: #687787; line-height: 1.5; margin-top: 12px; }
</style>
