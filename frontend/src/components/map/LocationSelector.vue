<template>
  <div class="location-selector" ref="rootRef">
    <label v-if="label">{{ label }}</label>
    <div class="ls-input-wrap">
      <input
        :value="inputText"
        :placeholder="placeholder"
        autocomplete="off"
        @input="onInput"
        @focus="onFocus"
        @keydown.down.prevent="moveActive(1)"
        @keydown.up.prevent="moveActive(-1)"
        @keydown.enter.prevent="confirmActive"
        @keydown.esc="closeDropdown"
      />
      <button v-if="inputText" type="button" class="ls-clear" title="清除" @click="clearAll">×</button>
    </div>

    <div class="ls-dropdown" v-if="open">
      <div v-if="loading" class="ls-hint">搜索中...</div>
      <div v-else-if="error" class="ls-error">{{ error }}</div>
      <div v-else-if="inputText.trim().length < 2" class="ls-hint">至少输入 2 个字</div>
      <div v-else-if="!tips.length" class="ls-hint">无匹配地点，请换个关键词</div>
      <button
        v-for="(tip, idx) in tips"
        :key="tip.id || tip.name + idx"
        type="button"
        class="ls-item"
        :class="{ active: idx === activeIndex }"
        @mousedown.prevent="selectTip(tip)"
      >
        <div class="ls-name">{{ tip.name }}</div>
        <div class="ls-meta">{{ tip.district }} {{ tip.address }}</div>
      </button>
    </div>

    <div class="ls-selected" v-if="isSelected">
      已选：{{ modelValue.name }}
      <span class="ls-coord">({{ modelValue.lng.toFixed(5) }}, {{ modelValue.lat.toFixed(5) }})</span>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { fetchLocationTips, isLocationSelected } from '../../api/mapApi.js'

const props = defineProps({
  label: { type: String, default: '' },
  placeholder: { type: String, default: '输入地点名称' },
  city: { type: String, default: '上海' },
  modelValue: {
    type: Object,
    default: () => ({ id: '', name: '', address: '', district: '', lng: null, lat: null })
  },
  debounceMs: { type: Number, default: 300 }
})

const emit = defineEmits(['update:modelValue'])

const rootRef = ref(null)
const inputText = ref(props.modelValue?.name || '')
const tips = ref([])
const open = ref(false)
const loading = ref(false)
const error = ref('')
const activeIndex = ref(-1)

let debounceTimer = null
let requestSeq = 0

const isSelected = computed(() => isLocationSelected(props.modelValue))

watch(
  () => props.modelValue,
  (v) => {
    if (v?.name && inputText.value !== v.name && isLocationSelected(v)) {
      inputText.value = v.name
    }
  },
  { deep: true }
)

function emitEmptyKeepingText(name) {
  emit('update:modelValue', {
    id: '',
    name: name || '',
    address: '',
    district: '',
    lng: null,
    lat: null
  })
}

function onInput(e) {
  const value = e.target.value
  inputText.value = value
  // 用户改字后视为未选定具体 POI
  emitEmptyKeepingText(value)
  scheduleSearch(value)
}

function onFocus() {
  open.value = true
  if (inputText.value.trim().length >= 2 && !tips.value.length && !loading.value) {
    scheduleSearch(inputText.value)
  }
}

function scheduleSearch(value) {
  clearTimeout(debounceTimer)
  error.value = ''
  const q = (value || '').trim()
  if (q.length < 2) {
    tips.value = []
    loading.value = false
    return
  }
  debounceTimer = setTimeout(() => runSearch(q), props.debounceMs)
}

async function runSearch(q) {
  const seq = ++requestSeq
  loading.value = true
  error.value = ''
  open.value = true
  try {
    const list = await fetchLocationTips(q, props.city)
    if (seq !== requestSeq) return
    tips.value = list
    activeIndex.value = list.length ? 0 : -1
  } catch (e) {
    if (seq !== requestSeq) return
    tips.value = []
    activeIndex.value = -1
    error.value = e?.message || '网络失败，请稍后重试'
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}

function selectTip(tip) {
  emit('update:modelValue', {
    id: tip.id || '',
    name: tip.name || '',
    address: tip.address || '',
    district: tip.district || '',
    lng: tip.lng,
    lat: tip.lat
  })
  inputText.value = tip.name || ''
  tips.value = []
  open.value = false
  activeIndex.value = -1
  error.value = ''
}

function clearAll() {
  inputText.value = ''
  tips.value = []
  open.value = false
  activeIndex.value = -1
  error.value = ''
  emitEmptyKeepingText('')
}

function closeDropdown() {
  open.value = false
}

function moveActive(delta) {
  if (!tips.value.length) return
  const n = tips.value.length
  activeIndex.value = (activeIndex.value + delta + n) % n
}

function confirmActive() {
  if (activeIndex.value >= 0 && tips.value[activeIndex.value]) {
    selectTip(tips.value[activeIndex.value])
  }
}

function onDocClick(e) {
  if (rootRef.value && !rootRef.value.contains(e.target)) {
    open.value = false
  }
}

onMounted(() => document.addEventListener('mousedown', onDocClick))
onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onDocClick)
  clearTimeout(debounceTimer)
})
</script>

<style scoped>
.location-selector {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 220px;
}
.location-selector label {
  font-size: 11px;
  color: #999;
  font-weight: 500;
}
.ls-input-wrap {
  position: relative;
  display: flex;
  align-items: center;
}
.ls-input-wrap input {
  width: 100%;
  padding: 5px 28px 5px 8px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 13px;
  box-sizing: border-box;
}
.ls-input-wrap input:focus {
  outline: none;
  border-color: #1890ff;
  box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.15);
}
.ls-clear {
  position: absolute;
  right: 4px;
  border: none;
  background: transparent;
  color: #999;
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
  padding: 2px 6px;
}
.ls-dropdown {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  right: 0;
  z-index: 40;
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.12);
  max-height: 260px;
  overflow-y: auto;
}
.ls-hint, .ls-error {
  padding: 10px 12px;
  font-size: 12px;
  color: #999;
}
.ls-error { color: #cf1322; }
.ls-item {
  display: block;
  width: 100%;
  text-align: left;
  border: none;
  background: #fff;
  padding: 8px 12px;
  cursor: pointer;
  border-bottom: 1px solid #f5f5f5;
}
.ls-item:hover, .ls-item.active {
  background: #e6f7ff;
}
.ls-name {
  font-size: 13px;
  color: #333;
  font-weight: 500;
}
.ls-meta {
  font-size: 11px;
  color: #999;
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ls-selected {
  font-size: 11px;
  color: #389e0d;
  margin-top: 2px;
}
.ls-coord {
  color: #8c8c8c;
  margin-left: 4px;
}
</style>
