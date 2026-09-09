<template>
  <div class="app-container">
    <!-- ========== 登录页 ========== -->
    <div class="page page-login" v-if="currentPage === 'login'">
      <div class="login-card">
        <div class="login-header">
          <h1>黑暗守卫者：基于车辆雾计算高效隐私保护轨迹验证demo</h1>
          <p>Privacy-Preserving Trajectory Verification via Vehicular Fog Computing</p>
        </div>
        <div class="login-body" v-if="loginMode === 'login'">
          <div class="form-group">
            <label>用户名</label>
            <input v-model="loginForm.username" placeholder="请输入用户名" @keyup.enter="handleLogin" />
          </div>
          <div class="form-group">
            <label>密码</label>
            <input v-model="loginForm.password" type="password" placeholder="请输入密码" @keyup.enter="handleLogin" />
          </div>
          <div class="login-error" v-if="loginError">{{ loginError }}</div>
          <button class="login-btn" @click="handleLogin">登录</button>
          <div class="login-switch">还没有账号？<a @click="loginMode='register'">注册</a></div>
          <!-- 隐藏调试入口：<div class="debug-entry"><a @click="currentPage='debug'" title="轨迹调试工具">🔧 轨迹调试</a></div> -->
        </div>
        <div class="login-body" v-else>
          <div class="form-group">
            <label>用户名</label>
            <input v-model="registerForm.username" placeholder="请输入用户名" />
          </div>
          <div class="form-group">
            <label>密码</label>
            <input v-model="registerForm.password" type="password" placeholder="请输入密码" />
          </div>
          <div class="form-group">
            <label>确认密码</label>
            <input v-model="registerForm.confirmPassword" type="password" placeholder="请再次输入密码" @keyup.enter="handleRegister" />
          </div>
          <div class="login-error" v-if="registerError">{{ registerError }}</div>
          <button class="login-btn" @click="handleRegister">注册</button>
          <div class="login-switch">已有账号？<a @click="loginMode='login'">登录</a></div>
        </div>
      </div>
    </div>

    <!-- ========== 主页：选择路线 ========== -->
    <div class="page page-main" v-if="currentPage === 'main'">
      <header class="app-header">
        <h1>黑暗守卫者：基于车辆雾计算高效隐私保护轨迹验证demo</h1>
        <span class="subtitle">Privacy-Preserving Trajectory Verification via Vehicular Fog Computing</span>
        <div class="header-right">
          <span class="user-info">{{ currentUser }}</span>
          <button class="logout-btn" @click="handleLogout">退出</button>
        </div>
      </header>

      <div class="toolbar toolbar-goal1">
        <div class="toolbar-left location-row">
          <LocationSelector
            v-model="startLocation"
            label="起点"
            placeholder="搜索起点，例如：华东师范大学"
          />
          <LocationSelector
            v-model="endLocation"
            label="终点"
            placeholder="搜索终点，例如：上海虹桥站"
          />
          <div class="form-row">
            <label>阈值 k</label>
            <select v-model.number="thresholdK">
              <option :value="2">2</option>
              <option :value="3">3</option>
              <option :value="4">4</option>
              <option :value="5">5</option>
            </select>
          </div>
          <button class="plan-btn" :disabled="isTraveling || planningLoading" @click="onPlanRoute">
            {{ planningLoading ? '规划中...' : '规划路线' }}
          </button>
        </div>

        <div class="toolbar-center" v-if="planHint">
          <span class="plan-hint" :class="planHintType">{{ planHint }}</span>
        </div>

        <div class="toolbar-demo">
          <div class="form-row">
            <label>行程场景</label>
            <select v-model="travelScenario">
              <option v-for="opt in scenarioSelectOptions" :key="opt.value" :value="opt.value">
                {{ opt.label }}
              </option>
            </select>
          </div>
          <button class="verify-btn" :disabled="!canStart || isTraveling || preparingTravel" @click="startTravel">
            {{ preparingTravel ? '生成绕路中...' : (isTraveling ? '行程中...' : '开始行程') }}
          </button>
        </div>
      </div>

      <div class="main-workspace">
        <div class="map-full">
          <div class="map-title">
            地图
            <span class="map-legend">
              <span class="legend-dot legend-start"></span>起点
              <span class="legend-dot legend-end"></span>终点
              <span class="legend-line legend-ref"></span>参考路线
              <span class="legend-line legend-alt"></span>备选路线
            </span>
          </div>
          <div class="map-container" ref="mapRef"></div>
        </div>
        <aside class="route-side" v-if="plannedRoutes.length || planningLoading">
          <div v-if="planningLoading" class="route-loading">正在规划三条可选路线...</div>
          <RoutePlanList
            :routes="plannedRoutes"
            :selected-id="selectedRouteId"
            @select="onSelectPlannedRoute"
          />
        </aside>
      </div>
    </div>

    <!-- ========== 行程页：地图动画 + 右侧面板 ========== -->
    <div class="page page-travel" v-if="currentPage === 'travel'">
      <header class="app-header travel-header">
        <h1>黑暗守卫者：行程验证</h1>
        <span class="subtitle">{{ currentTrajectory?.start }} → {{ currentTrajectory?.end }}</span>
        <span class="category-tag" :class="'cat-' + currentTrajectory?.category" v-if="currentTrajectory">
          {{ currentTrajectory?.label }}
        </span>
        <div class="header-right">
          <button class="back-btn" @click="goBackToMain" :disabled="isTraveling">
            <span class="back-arrow">←</span> 返回
          </button>
        </div>
      </header>

      <div class="travel-layout">
        <div class="travel-map-area">
          <div class="map-title">
            实时行程
            <span class="map-legend">
              <span class="legend-line legend-ref"></span>参考轨迹
              <span class="legend-line legend-user"></span>用户实际轨迹
              <span class="legend-line legend-alt"></span>绕路预览
            </span>
          </div>
          <div class="spatial-notice" v-if="spatialNotice" role="alert">
            <span>
              <strong>路线偏移提醒</strong>
              已检测到偏离参考路线
            </span>
            <button type="button" aria-label="关闭路线偏移提醒" @click="spatialNotice = null">×</button>
          </div>
          <div class="map-container" ref="travelMapRef"></div>
        </div>

        <div class="travel-sidebar">
          <!-- 计时器 -->
          <div class="timer-section">
            <div class="timer-label">行程计时 (模拟)</div>
            <div class="timer-display">{{ formatTimer(travelTimer) }}</div>
            <div class="timer-hint">1秒 = 1分钟</div>
          </div>

          <!-- PTMOC 密码数据流可视化（由 processTrace 驱动） -->
          <div class="crypto-section" v-if="showCryptoProcess">
            <CryptoFlowPanel
              :mode="cryptoMode"
              :summary="cryptoKey"
              :process-trace="cryptoProcessTrace"
              :total-duration="cryptoTotalTime"
              :auto-play="cryptoMode !== 'loading'"
              @complete="onCryptoFlowComplete"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- ========== 调试页：轨迹对比 ========== -->
    <div class="page page-debug" v-if="currentPage === 'debug'">
      <header class="app-header">
        <h1>轨迹对比调试工具</h1>
        <span class="subtitle">人民广场 → 世纪公园</span>
        <div class="header-right">
          <button class="back-btn" @click="currentPage='login'">
            <span class="back-arrow">←</span> 返回登录
          </button>
        </div>
      </header>
      <div class="debug-layout">
        <div class="debug-map-area">
          <div class="map-title">
            参考轨迹 vs 用户轨迹
            <span class="map-legend">
              <span class="legend-line legend-ref"></span>参考轨迹
              <span class="legend-line legend-user"></span>用户轨迹(alt)
              <span class="legend-line legend-fork"></span>分叉点
            </span>
          </div>
          <div class="map-container" ref="debugMapRef"></div>
        </div>
        <div class="debug-sidebar">
          <div class="debug-info-section">
            <h4>选中分叉点</h4>
            <div v-if="debugSelectedIndex >= 0" class="debug-selected-info">
              <div class="debug-data-row">
                <span class="data-label">参考轨迹索引:</span>
                <span class="data-value">{{ debugSelectedIndex }}</span>
              </div>
              <div class="debug-data-row">
                <span class="data-label">坐标(原始):</span>
                <span class="data-value">[{{ debugSelectedCoord }}]</span>
              </div>
              <div class="debug-data-row">
                <span class="data-label">说明:</span>
                <span class="data-value">此点之前完全走参考轨迹，此点之后走alt路线</span>
              </div>
            </div>
            <div v-else class="debug-hint">
              点击地图上的<b>橙色分叉标记</b>选择偏移点
            </div>
          </div>
          <div class="debug-info-section">
            <h4>分叉点列表</h4>
            <div class="debug-fork-list">
              <div 
                v-for="fork in debugForkPoints" :key="fork.refIndex"
                class="debug-fork-item"
                :class="{ selected: debugSelectedIndex === fork.refIndex }"
                @click="selectDebugFork(fork.refIndex)"
              >
                <span class="fork-idx">Ref[{{ fork.refIndex }}]</span>
                <span class="fork-dist">距离差: {{ fork.distDiff.toFixed(0) }}m</span>
              </div>
            </div>
          </div>
          <button class="debug-confirm-btn" :disabled="debugSelectedIndex < 0" @click="confirmDebugFork">
            确认选择并返回登录
          </button>
        </div>
      </div>
    </div>

    <!-- ========== 弹窗 ========== -->
    <Transition name="overlay">
      <div class="alert-overlay" v-if="alertInfo.show" @click.self="dismissAlert">
        <div class="alert-dialog" :class="'alert-' + alertInfo.type">
          <div class="alert-icon">{{ alertInfo.type === 'success' ? '✓' : '⚠' }}</div>
          <div class="alert-message">{{ alertInfo.message }}</div>
          <button class="alert-btn" @click="dismissAlert">确定</button>
        </div>
      </div>
    </Transition>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { ROUTE_COORDS } from '../data/trajectoryData.js'
import { buildScenarioTrajectory, scenarioOptions } from '../data/trajectoryScenario.js'
import { buildTraveledTrajectory, distanceToRouteMeters, SPATIAL_ALERT_THRESHOLD_METERS } from '../utils/routeDeviation.js'
import { verifyTrajectory, buildCryptoViewModel, buildSyntheticProcessTrace } from '../api/ptmocApi.js'
import { emptyLocation, isLocationSelected, planRoutes, planDetourRoute } from '../api/mapApi.js'
import LocationSelector from './map/LocationSelector.vue'
import RoutePlanList from './map/RoutePlanList.vue'
import CryptoFlowPanel from './crypto/CryptoFlowPanel.vue'

delete L.Icon.Default.prototype._getIconUrl
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
  iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
  shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
})

const REF_COLOR = '#006400'
const USER_COLOR = '#1890ff'

// 高德地图瓦片（中文标注，GCJ-02坐标系）
const TILE_URL = 'https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}'
const TILE_SUBDOMAINS = ['1', '2', '3', '4']

// ========== WGS-84 → GCJ-02 坐标转换 ==========
function _transformLat(x, y) {
  let ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x))
  ret += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0
  ret += (20.0 * Math.sin(y * Math.PI) + 40.0 * Math.sin(y / 3.0 * Math.PI)) * 2.0 / 3.0
  ret += (160.0 * Math.sin(y / 12.0 * Math.PI) + 320 * Math.sin(y * Math.PI / 30.0)) * 2.0 / 3.0
  return ret
}
function _transformLng(x, y) {
  let ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x))
  ret += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0
  ret += (20.0 * Math.sin(x * Math.PI) + 40.0 * Math.sin(x / 3.0 * Math.PI)) * 2.0 / 3.0
  ret += (150.0 * Math.sin(x / 12.0 * Math.PI) + 300.0 * Math.sin(x / 30.0 * Math.PI)) * 2.0 / 3.0
  return ret
}
function wgs84ToGcj02(lat, lng) {
  if (lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271) return { lat, lng }
  const a = 6378245.0, ee = 0.00669342162296594323
  let dLat = _transformLat(lng - 105.0, lat - 35.0)
  let dLng = _transformLng(lng - 105.0, lat - 35.0)
  const radLat = lat / 180.0 * Math.PI
  let magic = 1 - ee * Math.sin(radLat) * Math.sin(radLat)
  const sqrtMagic = Math.sqrt(magic)
  dLat = (dLat * 180.0) / ((a * (1 - ee)) / (magic * sqrtMagic) * Math.PI)
  dLng = (dLng * 180.0) / (a / sqrtMagic * Math.cos(radLat) * Math.PI)
  return { lat: lat + dLat, lng: lng + dLng }
}
// 将轨迹点转为地图展示坐标（GCJ-02）
function convertPts(pts, coordSystem) {
  const sys = coordSystem || currentTrajectory.value?.coordSystem || 'wgs84'
  if (sys === 'gcj02') {
    return pts.map((p) => ({ ...p, lat: p.lat, lng: p.lng }))
  }
  return pts.map((p) => {
    const c = wgs84ToGcj02(p.lat, p.lng)
    return { ...p, lat: c.lat, lng: c.lng }
  })
}

function createColorIcon(color) {
  return L.divIcon({
    className: 'custom-marker',
    html: `<div style="width:28px;height:28px;border-radius:50%;background:${color};border:3px solid white;box-shadow:0 2px 8px rgba(0,0,0,0.3);display:flex;align-items:center;justify-content:center;">
      <div style="width:8px;height:8px;border-radius:50%;background:white;"></div>
    </div>`,
    iconSize: [28, 28],
    iconAnchor: [14, 14]
  })
}

// 小车图标
function createCarIcon() {
  return L.divIcon({
    className: 'car-marker',
    html: `<div style="font-size:24px;line-height:1;filter:drop-shadow(0 2px 3px rgba(0,0,0,0.4));">🚕</div>`,
    iconSize: [28, 28],
    iconAnchor: [14, 14]
  })
}

// ========== 预生成的PTMOC加密数据 ==========
const CRYPTO_DATA = {
  key: 'RSA-2048 KeyPair (sk: 0x3F7A...B9C1, pk: 0x8E2D...4F6A)',
  totalDuration: 5408,
  steps: [
    {
      name: '系统初始化 PTMOC.Setup',
      data: [
        { label: '安全参数 λ', value: '256 bits' },
        { label: '大素数 p₀', value: '0xA1B2C3...F7E8 (256bit)' },
        { label: '陷门置换对', value: '(RSA-2048, N=0x9D4F...2A1C)' }
      ]
    },
    {
      name: '密钥生成 PTMOC.KeyGen',
      data: [
        { label: 'Sender 密钥', value: 'sk_s=0x7B3E...D2A8, pk_s=0x1F4C...9E37' },
        { label: 'Server 密钥', value: 'sk_v=0x5A8D...C1F4, pk_v=0xE27B...3D61' },
        { label: 'CSP 密钥', value: 'sk_c=0x2F6A...B8E3, pk_c=0xD491...7C52' },
        { label: 'Receiver 密钥', value: 'sk_r=0x8C1F...4A7D, pk_r=0x6E53...F109' }
      ]
    },
    {
      name: '消息编码',
      data: [
        { label: '原文 m₁', value: '0x0000000000000000' },
        { label: '原文 m₂', value: '0x0000000000000000' },
        { label: '原文 m₃', value: '0x0000000000000000' }
      ]
    },
    {
      name: 'PTMOC加密 Enc',
      data: [
        { label: '密文 c₁', value: '0x4F8A2C...E71B3D (512bytes)' },
        { label: '密文 c₂', value: '0x9D3E7F...A2C851 (512bytes)' },
        { label: '密文 c₃', value: '0xB1764E...5D9AF2 (512bytes)' }
      ]
    },
    {
      name: '密文域评估 Eval',
      data: [
        { label: '多项式密文结果', value: '0x7C2E91...F4A8B3 (512bytes)' },
        { label: '随机掩码 r\'', value: '0x3A8D5F...1E7C42 (256bit)' }
      ]
    },
    {
      name: '结果解密 Dec',
      data: [
        { label: '解密 r', value: '0xB4F172...9E3A61' },
        { label: '解密 r\'', value: '0x5C8D23...7A2F19' },
        { label: '最终结果 f(m₁,m₂,m₃)', value: '' }
      ]
    }
  ]
}

// 异常样例的原文/密文不同
const CRYPTO_DATA_ABNORMAL = {
  key: CRYPTO_DATA.key,
  totalDuration: 4469,
  steps: [
    {
      name: '系统初始化 PTMOC.Setup',
      data: [
        { label: '安全参数 λ', value: '256 bits' },
        { label: '大素数 p₀', value: '0xA1B2C3...F7E8 (256bit)' },
        { label: '陷门置换对', value: '(RSA-2048, N=0x9D4F...2A1C)' }
      ]
    },
    {
      name: '密钥生成 PTMOC.KeyGen',
      data: [
        { label: 'Sender 密钥', value: 'sk_s=0x7B3E...D2A8, pk_s=0x1F4C...9E37' },
        { label: 'Server 密钥', value: 'sk_v=0x5A8D...C1F4, pk_v=0xE27B...3D61' },
        { label: 'CSP 密钥', value: 'sk_c=0x2F6A...B8E3, pk_c=0xD491...7C52' },
        { label: 'Receiver 密钥', value: 'sk_r=0x8C1F...4A7D, pk_r=0x6E53...F109' }
      ]
    },
    {
      name: '消息编码',
      data: [
        { label: '原文 m₁', value: '0x00012A8F3C7D0000' },
        { label: '原文 m₂', value: '0x00005E2B4A190000' },
        { label: '原文 m₃', value: '0x000000001E848000' }
      ]
    },
    {
      name: 'PTMOC加密 Enc',
      data: [
        { label: '密文 c₁', value: '0x8B3F1E...D4A72C (512bytes)' },
        { label: '密文 c₂', value: '0x2E9C4D...7F1B83 (512bytes)' },
        { label: '密文 c₃', value: '0x6A5E12...C93D78 (512bytes)' }
      ]
    },
    {
      name: '密文域评估 Eval',
      data: [
        { label: '多项式密文结果', value: '0xD4F283...1A7E56 (512bytes)' },
        { label: '随机掩码 r\'', value: '0xE291AF...4C3D82 (256bit)' }
      ]
    },
    {
      name: '结果解密 Dec',
      data: [
        { label: '解密 r', value: '0xF3A8D1...5B72E4' },
        { label: '解密 r\'', value: '0x1E7C4A...D8F396' },
        { label: '最终结果 f(m₁,m₂,m₃)', value: '' }
      ]
    }
  ]
}

// ========== 登录状态 ==========
const currentPage = ref('login')
const loginMode = ref('login')
const loginForm = ref({ username: '', password: '' })
const registerForm = ref({ username: '', password: '', confirmPassword: '' })
const loginError = ref('')
const registerError = ref('')
const currentUser = ref('')

// 硬编码账号 + localStorage缓存注册账号
const HARDCODED_ACCOUNTS = { 'admin': 'admin123', 'test': 'test123' }

function getRegisteredAccounts() {
  try {
    return JSON.parse(localStorage.getItem('ptmoc_accounts') || '{}')
  } catch { return {} }
}

function handleLogin() {
  loginError.value = ''
  const { username, password } = loginForm.value
  if (!username || !password) { loginError.value = '请输入用户名和密码'; return }

  const registered = getRegisteredAccounts()
  if (HARDCODED_ACCOUNTS[username] === password || registered[username] === password) {
    currentUser.value = username
    currentPage.value = 'main'
  } else {
    loginError.value = '用户名或密码错误'
  }
}

function handleRegister() {
  registerError.value = ''
  const { username, password, confirmPassword } = registerForm.value
  if (!username || !password) { registerError.value = '请输入用户名和密码'; return }
  if (password !== confirmPassword) { registerError.value = '两次密码不一致'; return }
  if (password.length < 4) { registerError.value = '密码至少4位'; return }

  const registered = getRegisteredAccounts()
  if (HARDCODED_ACCOUNTS[username]) { registerError.value = '该用户名已被占用'; return }
  if (registered[username]) { registerError.value = '该用户名已注册'; return }

  registered[username] = password
  localStorage.setItem('ptmoc_accounts', JSON.stringify(registered))
  registerError.value = ''
  loginMode.value = 'login'
  loginForm.value.username = username
  loginForm.value.password = ''
}

function handleLogout() {
  currentUser.value = ''
  currentPage.value = 'login'
  loginForm.value = { username: '', password: '' }
}

// ========== 主页状态 ==========
const thresholdK = ref(3)
const currentTrajectory = ref(null)
const isTraveling = ref(false)

const startLocation = ref(emptyLocation())
const endLocation = ref(emptyLocation())
const planHint = ref('')
const planHintType = ref('info') // info | ok | warn
const plannedRoutes = ref([])
const selectedRouteId = ref('')
const selectedPlannedRoute = ref(null)
const planningLoading = ref(false)
const travelScenario = ref('pass')
const scenarioSelectOptions = scenarioOptions()
const preparingTravel = ref(false)

const canStart = computed(() => {
  if (isTraveling.value || preparingTravel.value) return false
  return !!(selectedPlannedRoute.value && (selectedPlannedRoute.value.polyline || []).length >= 2)
})

// 地图
const mapRef = ref(null)
const travelMapRef = ref(null)
let mainMap = null
let travelMap = null
let startMarker = null
let endMarker = null
let plannedPolylines = []

function clearEndpointMarkers() {
  if (startMarker) { mainMap?.removeLayer(startMarker); startMarker = null }
  if (endMarker) { mainMap?.removeLayer(endMarker); endMarker = null }
}

function clearPlannedPolylines() {
  plannedPolylines.forEach(l => { try { mainMap?.removeLayer(l) } catch (_) {} })
  plannedPolylines = []
}

function clearMainOverlays() {
  if (!mainMap) return
  mainMap.eachLayer(l => { if (!(l instanceof L.TileLayer)) mainMap.removeLayer(l) })
  startMarker = null
  endMarker = null
  plannedPolylines = []
}

function refreshEndpointMarkers() {
  if (!mainMap) return
  if (currentTrajectory.value) return

  clearEndpointMarkers()
  const boundsPts = []

  if (isLocationSelected(startLocation.value)) {
    const lat = startLocation.value.lat
    const lng = startLocation.value.lng
    startMarker = L.marker([lat, lng], { icon: createColorIcon('#52c41a') })
      .addTo(mainMap)
      .bindPopup(`起点: ${startLocation.value.name}`)
    boundsPts.push([lat, lng])
  }
  if (isLocationSelected(endLocation.value)) {
    const lat = endLocation.value.lat
    const lng = endLocation.value.lng
    endMarker = L.marker([lat, lng], { icon: createColorIcon('#ff4d4f') })
      .addTo(mainMap)
      .bindPopup(`终点: ${endLocation.value.name}`)
    boundsPts.push([lat, lng])
  }

  if (!plannedRoutes.value.length) {
    if (boundsPts.length === 1) mainMap.setView(boundsPts[0], 14)
    else if (boundsPts.length === 2) mainMap.fitBounds(L.latLngBounds(boundsPts), { padding: [60, 60] })
  }
}

function drawPlannedRoutes() {
  if (!mainMap) return
  clearPlannedPolylines()

  const allLatLngs = []
  plannedRoutes.value.forEach((route) => {
    const latlngs = (route.polyline || []).map(p => [p.lat, p.lng])
    if (!latlngs.length) return
    const selected = route.routeId === selectedRouteId.value
    const line = L.polyline(latlngs, {
      color: selected ? '#1890ff' : '#8c8c8c',
      weight: selected ? 6 : 3,
      opacity: selected ? 0.95 : 0.45,
      dashArray: selected ? null : '6, 8'
    }).addTo(mainMap)
    line.bindPopup(`${route.name} · ${(route.distanceMeters / 1000).toFixed(1)} km`)
    plannedPolylines.push(line)
    latlngs.forEach(ll => allLatLngs.push(ll))
  })

  refreshEndpointMarkers()

  if (allLatLngs.length) {
    mainMap.fitBounds(L.latLngBounds(allLatLngs), { padding: [50, 50] })
  }
}

function onSelectPlannedRoute(route) {
  selectedRouteId.value = route.routeId
  selectedPlannedRoute.value = route
  currentTrajectory.value = null
  planHintType.value = 'ok'
  planHint.value = `已选择参考路线：${route.name}（${(route.distanceMeters / 1000).toFixed(1)} km / ${Math.round(route.durationSeconds / 60)} min）· 场景「${scenarioLabel(travelScenario.value)}」后可开始行程`
  drawPlannedRoutes()
}

function scenarioLabel(value) {
  const hit = scenarioSelectOptions.find((o) => o.value === value)
  return hit ? hit.label : value
}

async function onPlanRoute() {
  if (!isLocationSelected(startLocation.value) || !isLocationSelected(endLocation.value)) {
    planHintType.value = 'warn'
    planHint.value = '请从候选列表中分别选择具体的起点和终点（仅输入文字不能规划）'
    showAlert('warning', '请从下拉候选中选择具体 POI，不能只输入文字。')
    return
  }

  currentTrajectory.value = null
  plannedRoutes.value = []
  selectedRouteId.value = ''
  selectedPlannedRoute.value = null
  clearMainOverlays()
  refreshEndpointMarkers()

  planningLoading.value = true
  planHintType.value = 'info'
  planHint.value = '正在规划：推荐路线 / 躲避拥堵 / 速度最快 ...'

  try {
    const routes = await planRoutes(
      { lng: startLocation.value.lng, lat: startLocation.value.lat },
      { lng: endLocation.value.lng, lat: endLocation.value.lat }
    )
    plannedRoutes.value = routes
    if (!routes.length) {
      planHintType.value = 'warn'
      planHint.value = '未返回可用路线，请换一组起终点重试'
      return
    }
    // 默认选中第一条（推荐路线）
    onSelectPlannedRoute(routes[0])
    planHintType.value = 'ok'
    planHint.value = `已规划 ${routes.length} 条路线，默认选中「${routes[0].name}」作为参考路线`
  } catch (err) {
    planHintType.value = 'warn'
    const msg = formatPlanError(err?.message) || '路径规划失败'
    planHint.value = msg
    showAlert('warning', msg)
  } finally {
    planningLoading.value = false
  }
}

function formatPlanError(raw) {
  if (!raw) return ''
  const u = String(raw).toUpperCase()
  if (u.includes('CUQPS') || u.includes('EXCEEDED_THE_LIMIT') || u.includes('DAILY_QUERY')) {
    return '地图服务请求过于频繁，请等待几秒后再点「规划路线」'
  }
  return String(raw)
}

watch(startLocation, () => {
  // 起终点变更后清空旧路线，避免错配
  if (plannedRoutes.value.length) {
    plannedRoutes.value = []
    selectedRouteId.value = ''
    selectedPlannedRoute.value = null
    clearPlannedPolylines()
  }
  if (!currentTrajectory.value) {
    nextTick(() => refreshEndpointMarkers())
  }
}, { deep: true })

watch(endLocation, () => {
  if (plannedRoutes.value.length) {
    plannedRoutes.value = []
    selectedRouteId.value = ''
    selectedPlannedRoute.value = null
    clearPlannedPolylines()
  }
  if (!currentTrajectory.value) {
    nextTick(() => refreshEndpointMarkers())
  }
}, { deep: true })

function updateMainMap() {
  if (!mainMap || !currentTrajectory.value) return
  mainMap.invalidateSize()
  clearMainOverlays()

  const refPts = convertPts(
    currentTrajectory.value.referenceTrajectory,
    currentTrajectory.value.coordSystem
  )
  const refLatLngs = refPts.map(p => [p.lat, p.lng])
  L.polyline(refLatLngs, { color: REF_COLOR, weight: 3, opacity: 0.7, dashArray: '10, 7' }).addTo(mainMap)

  startMarker = L.marker(refLatLngs[0], { icon: createColorIcon('#52c41a') }).addTo(mainMap).bindPopup('起点: ' + (currentTrajectory.value.start || '起点'))
  endMarker = L.marker(refLatLngs[refLatLngs.length - 1], { icon: createColorIcon('#ff4d4f') }).addTo(mainMap).bindPopup('终点: ' + (currentTrajectory.value.end || '终点'))

  mainMap.fitBounds(L.latLngBounds(refLatLngs), { padding: [50, 50] })
}

function initMainMap() {
  if (mapRef.value && !mainMap) {
    mainMap = L.map(mapRef.value, { attributionControl: false, zoomControl: true }).setView([31.23, 121.47], 12)
    L.tileLayer(TILE_URL, { maxZoom: 18, subdomains: TILE_SUBDOMAINS }).addTo(mainMap)
    setTimeout(() => {
      if (mainMap) {
        mainMap.invalidateSize()
        if (currentTrajectory.value) updateMainMap()
        else if (plannedRoutes.value.length) drawPlannedRoutes()
        else refreshEndpointMarkers()
      }
    }, 200)
  }
}

// ========== 行程页状态 ==========
const travelTimer = ref(0) // 模拟分钟数（1真实秒=1模拟分钟）
const spatialNotice = ref(null)
const showCryptoProcess = ref(false)
const cryptoSteps = ref([])
const cryptoProcessTrace = ref([])
const cryptoResult = ref(null)
const cryptoTotalTime = ref('')
const cryptoKey = ref('')
const cryptoMode = ref('offline') // online | offline | loading
const cryptoScore = ref(null)
const cryptoVerificationStatus = ref('')
const cryptoDecryptedResult = ref('')

let travelTimerInterval = null
let pathAnimTimeout = null
let userPolyline = null
let carMarker = null
let currentUserLatLngs = []
let travelAlertFired = false

// 弹窗
const alertInfo = ref({ show: false, type: 'success', message: '' })

// ========== 调试页状态 ==========
const debugMapRef = ref(null)
let debugMap = null
const debugSelectedIndex = ref(-1)
const debugSelectedCoord = ref('')
const debugForkPoints = ref([])

function selectDebugFork(idx) {
  debugSelectedIndex.value = idx
  const refCoords = ROUTE_COORDS['人民广场→世纪公园']
  const pt = refCoords[idx]
  debugSelectedCoord.value = `${pt[0]}, ${pt[1]}`
  // 高亮地图上对应的标记
  if (debugMap) {
    debugMap.eachLayer(l => {
      if (l._debugForkIdx !== undefined) {
        const el = l.getElement()
        if (el) {
          el.style.transform = l._debugForkIdx === idx ? 'scale(1.5)' : 'scale(1)'
          el.style.zIndex = l._debugForkIdx === idx ? '1000' : ''
        }
      }
    })
  }
}

// 用户选中的偏离点索引（持久化存储）
const selectedForkIndex = ref(-1)

function confirmDebugFork() {
  if (debugSelectedIndex.value >= 0) {
    selectedForkIndex.value = debugSelectedIndex.value
    console.log(`[调试] 用户选中偏离点索引: ${debugSelectedIndex.value}, 坐标: ${debugSelectedCoord.value}`)
    currentPage.value = 'login'
  }
}

function initDebugMap() {
  if (!debugMapRef.value) return
  
  if (debugMap) { debugMap.remove(); debugMap = null }
  
  debugMap = L.map(debugMapRef.value, { attributionControl: false, zoomControl: true }).setView([31.23, 121.50], 13)
  L.tileLayer(TILE_URL, { maxZoom: 18, subdomains: TILE_SUBDOMAINS }).addTo(debugMap)
  
  const refCoords = ROUTE_COORDS['人民广场→世纪公园']
  const altCoords = ROUTE_COORDS['人民广场→世纪公园_alt']
  
  if (!refCoords || !altCoords) return
  
  // 转换坐标
  const refPts = refCoords.map(c => { const gc = wgs84ToGcj02(c[0], c[1]); return [gc.lat, gc.lng] })
  const altPts = altCoords.map(c => { const gc = wgs84ToGcj02(c[0], c[1]); return [gc.lat, gc.lng] })
  
  // 画参考轨迹（绿色虚线）
  L.polyline(refPts, { color: REF_COLOR, weight: 4, opacity: 0.7, dashArray: '10, 7' }).addTo(debugMap)
  
  // 画alt轨迹（蓝色实线）
  L.polyline(altPts, { color: USER_COLOR, weight: 4, opacity: 0.7 }).addTo(debugMap)
  
  // 标记每个参考轨迹点（带索引号）
  refPts.forEach((pt, i) => {
    L.marker(pt, {
      icon: L.divIcon({
        className: 'debug-idx-marker',
        html: `<div style="background:#006400;color:white;font-size:9px;width:20px;height:20px;border-radius:50%;display:flex;align-items:center;justify-content:center;border:2px solid white;box-shadow:0 1px 4px rgba(0,0,0,0.3);">${i}</div>`,
        iconSize: [20, 20],
        iconAnchor: [10, 10]
      })
    }).addTo(debugMap).bindPopup(`参考轨迹点 [${i}]<br>原始: [${refCoords[i][0]}, ${refCoords[i][1]}]`)
  })
  
  // 标记每个alt轨迹点（带索引号）
  altPts.forEach((pt, i) => {
    L.marker(pt, {
      icon: L.divIcon({
        className: 'debug-idx-marker',
        html: `<div style="background:#1890ff;color:white;font-size:9px;width:20px;height:20px;border-radius:50%;display:flex;align-items:center;justify-content:center;border:2px solid white;box-shadow:0 1px 4px rgba(0,0,0,0.3);">${i}</div>`,
        iconSize: [20, 20],
        iconAnchor: [10, 10]
      })
    }).addTo(debugMap).bindPopup(`Alt轨迹点 [${i}]<br>原始: [${altCoords[i][0]}, ${altCoords[i][1]}]`)
  })
  
  // 找分叉点：对比同索引位置的ref和alt点距离
  const forks = []
  const minLen = Math.min(refPts.length, altPts.length)
  for (let i = 0; i < minLen; i++) {
    const rLat = refCoords[i][0], rLng = refCoords[i][1]
    const aLat = altCoords[i][0], aLng = altCoords[i][1]
    const dlat = rLat - aLat
    const dlng = rLng - aLng
    const distM = Math.sqrt(dlat * dlat + dlng * dlng) * 111000
    
    if (distM > 50) { // 距离差>50米视为分叉
      forks.push({ refIndex: i, distDiff: distM })
      
      // 在地图上标记分叉点（橙色大标记）
      const marker = L.marker(refPts[i], {
        icon: L.divIcon({
          className: 'debug-fork-marker',
          html: `<div style="background:#fa8c16;color:white;font-size:10px;width:26px;height:26px;border-radius:50%;display:flex;align-items:center;justify-content:center;border:3px solid #fff;box-shadow:0 2px 8px rgba(250,140,22,0.6);cursor:pointer;">${i}</div>`,
          iconSize: [26, 26],
          iconAnchor: [13, 13]
        })
      }).addTo(debugMap)
      
      marker._debugForkIdx = i
      marker.bindPopup(`分叉点 Ref[${i}]<br>距离差: ${distM.toFixed(0)}m<br><b>点击右侧列表选中此点</b>`)
      marker.on('click', () => selectDebugFork(i))
    }
  }
  
  debugForkPoints.value = forks
  
  // 起终点
  L.marker(refPts[0], { icon: createColorIcon('#52c41a') }).addTo(debugMap).bindPopup('起点')
  L.marker(refPts[refPts.length - 1], { icon: createColorIcon('#ff4d4f') }).addTo(debugMap).bindPopup('终点')
  
  debugMap.fitBounds(L.latLngBounds([...refPts, ...altPts]), { padding: [50, 50] })
}

function showAlert(type, message) {
  alertInfo.value = { show: true, type, message }
}

function dismissAlert() {
  alertInfo.value.show = false
  // 弹窗关闭后显示PTMOC验证过程
  if (!showCryptoProcess.value) {
    showCryptoProcess.value = true
    animateCryptoProcess()
  }
}

function formatTimer(minutes) {
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return `${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}`
}

// ========== 开始行程 ==========
async function prepareTrajectoryForTravel() {
  if (!(selectedPlannedRoute.value && (selectedPlannedRoute.value.polyline || []).length >= 2)) {
    throw new Error('请先规划并选择一条参考路线')
  }

  const route = selectedPlannedRoute.value
  const baseOpts = {
    referencePoints: route.polyline,
    startName: startLocation.value?.name || '起点',
    endName: endLocation.value?.name || '终点',
    durationSeconds: route.durationSeconds,
    scenario: travelScenario.value,
    coordSystem: 'gcj02',
    sourceRouteId: route.routeId
  }

  if (travelScenario.value !== 'spatial') {
    return buildScenarioTrajectory(baseOpts)
  }

  // 空间异常：走真实道路绕路（备选策略路线，或途经点二次规划）
  planHintType.value = 'info'
  planHint.value = '正在生成真实道路绕路（优先用备选路线，必要时途经点绕路）...'

  const detour = await planDetourRoute({
    origin: { lng: startLocation.value.lng, lat: startLocation.value.lat },
    destination: { lng: endLocation.value.lng, lat: endLocation.value.lat },
    referencePolyline: route.polyline,
    candidateRoutes: plannedRoutes.value,
    selectedRouteId: selectedRouteId.value
  })

  const sourceHint = detour.source === 'candidate'
    ? `选用已规划的「${detour.name}」`
    : '经旁路途经点重新规划'
  const detourDesc =
    `用户实际${sourceHint}绕行，未沿参考路线行驶（最大偏离约 ${Math.round(detour.maxDeviation)} m）`

  planHintType.value = 'ok'
  planHint.value = `空间异常已就绪：${sourceHint}，最大偏离约 ${Math.round(detour.maxDeviation)} m`

  return buildScenarioTrajectory({
    ...baseOpts,
    detourPoints: detour.polyline,
    detourDesc,
    detourDurationSeconds: detour.durationSeconds,
    detourRouteId: detour.routeId,
    detourName: detour.name
  })
}

async function startTravel() {
  if (!canStart.value || isTraveling.value || preparingTravel.value) return

  preparingTravel.value = true
  let traj
  try {
    traj = await prepareTrajectoryForTravel()
  } catch (err) {
    showAlert('warning', formatPlanError(err?.message) || '无法生成行程轨迹')
    preparingTravel.value = false
    return
  } finally {
    preparingTravel.value = false
  }
  currentTrajectory.value = traj

  // 清理之前的定时器
  if (pathAnimTimeout) { cancelAnimationFrame(pathAnimTimeout); pathAnimTimeout = null }
  if (travelTimerInterval) { clearInterval(travelTimerInterval); travelTimerInterval = null }

  // 销毁mainMap（v-if切换会销毁DOM，返回时需要重建）
  if (mainMap) { mainMap.remove(); mainMap = null }

  isTraveling.value = true
  currentPage.value = 'travel'
  travelTimer.value = 0
  spatialNotice.value = null
  showCryptoProcess.value = false
  cryptoSteps.value = []
  cryptoProcessTrace.value = []
  cryptoResult.value = null
  cryptoTotalTime.value = ''
  cryptoKey.value = ''
  cryptoMode.value = 'offline'
  cryptoScore.value = null
  cryptoVerificationStatus.value = ''
  cryptoDecryptedResult.value = ''
  currentUserLatLngs = []
  travelAlertFired = false
  userPolyline = null
  carMarker = null

  nextTick(() => {
    initTravelMap()
    startPathAnimation()
  })
}

function initTravelMap() {
  if (travelMapRef.value && !travelMap) {
    travelMap = L.map(travelMapRef.value, { attributionControl: false, zoomControl: true }).setView([31.23, 121.47], 12)
    L.tileLayer(TILE_URL, { maxZoom: 18, subdomains: TILE_SUBDOMAINS }).addTo(travelMap)
  }

  if (travelMap && currentTrajectory.value) {
    travelMap.invalidateSize()
    travelMap.eachLayer(l => { if (!(l instanceof L.TileLayer)) travelMap.removeLayer(l) })

    // 参考轨迹
    const refPts = convertPts(
      currentTrajectory.value.referenceTrajectory,
      currentTrajectory.value.coordSystem
    )
    const refLatLngs = refPts.map(p => [p.lat, p.lng])
    L.polyline(refLatLngs, { color: REF_COLOR, weight: 3, opacity: 0.5, dashArray: '10, 7' }).addTo(travelMap)

    // 空间异常时预先淡显完整绕路，方便评委一眼看出「走的是另一条路」
    const userFull = convertPts(
      currentTrajectory.value.userTrajectory,
      currentTrajectory.value.coordSystem
    )
    const userFullLatLngs = userFull.map(p => [p.lat, p.lng])
    if (
      currentTrajectory.value.category === 'spatial' &&
      userFullLatLngs.length >= 2
    ) {
      L.polyline(userFullLatLngs, {
        color: USER_COLOR,
        weight: 3,
        opacity: 0.28,
        dashArray: '2, 8'
      }).addTo(travelMap).bindPopup(currentTrajectory.value.detourName
        ? `实际绕路：${currentTrajectory.value.detourName}`
        : '实际绕路预览')
    }

    L.marker(refLatLngs[0], { icon: createColorIcon('#52c41a') }).addTo(travelMap).bindPopup('起点')
    L.marker(refLatLngs[refLatLngs.length - 1], { icon: createColorIcon('#ff4d4f') }).addTo(travelMap).bindPopup('终点')

    // 用户轨迹 - 空的，会逐步添加
    userPolyline = L.polyline([], { color: USER_COLOR, weight: 4, opacity: 0.9 }).addTo(travelMap)

    // 小车标记 - 初始在起点（用户轨迹起点）
    const carStart = userFullLatLngs[0] || refLatLngs[0]
    carMarker = L.marker(carStart, { icon: createCarIcon(), zIndexOffset: 1000 }).addTo(travelMap)

    const fitPts = refLatLngs.concat(userFullLatLngs)
    travelMap.fitBounds(L.latLngBounds(fitPts), { padding: [50, 50] })
  }
}

function startPathAnimation() {
  const category = currentTrajectory.value.category
  const coordSystem = currentTrajectory.value.coordSystem || 'wgs84'
  // 动画始终跟随「用户轨迹」（场景生成器已写入正常/偏移路径）
  const displayPts = convertPts(currentTrajectory.value.userTrajectory, coordSystem)
  const referencePts = convertPts(currentTrajectory.value.referenceTrajectory, coordSystem)
  let spatialAlertShown = false

  const totalDisplayPoints = displayPts.length

  function pointDist(p1, p2) {
    const latDiff = p1.lat - p2.lat
    const lngDiff = p1.lng - p2.lng
    return Math.sqrt(latDiff * latDiff + lngDiff * lngDiff) * 111000
  }

  let totalDist = 0
  const segDistances = []
  const cumDistances = [0]
  for (let i = 1; i < totalDisplayPoints; i++) {
    const d = pointDist(displayPts[i - 1], displayPts[i])
    segDistances.push(d)
    totalDist += d
    cumDistances.push(totalDist)
  }

  const totalDuration = 20000
  const speed = totalDist > 0 ? totalDist / totalDuration : 0

  const timeAnomalyStopDist = category === 'time'
    ? cumDistances[Math.floor(totalDisplayPoints * 0.45)]
    : -1

  travelTimerInterval = setInterval(() => {
    travelTimer.value++
  }, 1000)

  let animStartTime = null
  let animPausedAt = null
  let timeAnomalyPaused = false
  let timeAnomalyResumeTime = null
  let animFinished = false

  function getPositionAtDist(dist) {
    if (dist <= 0) return { lat: displayPts[0].lat, lng: displayPts[0].lng, segIdx: 0, fraction: 0 }
    if (dist >= totalDist) {
      const last = displayPts[displayPts.length - 1]
      return { lat: last.lat, lng: last.lng, segIdx: Math.max(0, displayPts.length - 2), fraction: 1 }
    }
    let segIdx = 0
    for (let i = 1; i < cumDistances.length; i++) {
      if (dist <= cumDistances[i]) { segIdx = i - 1; break }
    }
    const segStart = cumDistances[segIdx]
    const segLen = segDistances[segIdx]
    const t = segLen > 0 ? (dist - segStart) / segLen : 0
    const p1 = displayPts[segIdx]
    const p2 = displayPts[segIdx + 1]
    return {
      lat: p1.lat + (p2.lat - p1.lat) * t,
      lng: p1.lng + (p2.lng - p1.lng) * t,
      segIdx,
      fraction: t
    }
  }

  function getBlueLinePoints(dist) {
    const pos = getPositionAtDist(dist)
    const pts = []
    for (let i = 0; i <= pos.segIdx; i++) {
      pts.push([displayPts[i].lat, displayPts[i].lng])
    }
    const lastWP = displayPts[pos.segIdx]
    if (Math.abs(pos.lat - lastWP.lat) > 1e-8 || Math.abs(pos.lng - lastWP.lng) > 1e-8) {
      pts.push([pos.lat, pos.lng])
    }
    return pts
  }

  function animFrame(timestamp) {
    if (animFinished || travelAlertFired) return

    if (!animStartTime) animStartTime = timestamp

    if (timeAnomalyPaused) {
      if (timestamp >= timeAnomalyResumeTime) {
        timeAnomalyPaused = false
        animStartTime = timestamp - (animPausedAt / speed)
      } else {
        pathAnimTimeout = requestAnimationFrame(animFrame)
        return
      }
    }

    const elapsed = timestamp - animStartTime
    const currentDist = Math.min(speed * elapsed, totalDist)
    const pos = getPositionAtDist(currentDist)

    if (carMarker) {
      carMarker.setLatLng([pos.lat, pos.lng])
    }

    const bluePts = getBlueLinePoints(currentDist)
    if (bluePts.length >= 2) {
      currentUserLatLngs = bluePts
      userPolyline.setLatLngs(currentUserLatLngs)
    }

    if (travelMap) {
      travelMap.panTo([pos.lat, pos.lng], { animate: false })
    }

    if (category === 'time' && timeAnomalyStopDist > 0 && currentDist >= timeAnomalyStopDist && !timeAnomalyPaused) {
      timeAnomalyPaused = true
      animPausedAt = currentDist
      setTimeout(() => {
        animFinished = true
        travelAlertFired = true
        clearInterval(travelTimerInterval)
        travelTimerInterval = null
        isTraveling.value = false
        showAlert('warning', '时间异常！停留时间过久，已触发PTMOC验证。')
      }, 5000)
      return
    }

    if (category === 'spatial') {
      if (!spatialAlertShown && referencePts.length >= 2) {
        const deviationMeters = distanceToRouteMeters(pos, referencePts)
        if (deviationMeters > SPATIAL_ALERT_THRESHOLD_METERS) {
          spatialAlertShown = true
          spatialNotice.value = { distanceMeters: Math.round(deviationMeters) }
          const traveled = buildTraveledTrajectory(currentTrajectory.value, pos.segIdx, pos.fraction)
          showCryptoProcess.value = true
          // 异步验证截至告警时刻的轨迹，不等待结果、不暂停动画。
          void animateCryptoProcess(traveled)
        }
      }

      if (currentDist >= totalDist) {
        animFinished = true
        clearInterval(travelTimerInterval)
        travelTimerInterval = null
        isTraveling.value = false
        // 已在超阈值时触发验证，终点只结束行程。
        return
      }
    }

    if (category === 'pass' && currentDist >= totalDist) {
      animFinished = true
      clearInterval(travelTimerInterval)
      travelTimerInterval = null
      isTraveling.value = false
      setTimeout(() => {
        showAlert('success', '您的行程受PTMOC隐私加密算法验证，全程时空无偏移。')
      }, 1500)
      return
    }

    if (currentDist < totalDist) {
      pathAnimTimeout = requestAnimationFrame(animFrame)
    }
  }

  pathAnimTimeout = requestAnimationFrame(animFrame)
}

// ========== PTMOC 密码流程可视化（processTrace 重放） ==========
function playCryptoViewModel(viewModel) {
  cryptoMode.value = viewModel.mode
  cryptoKey.value = viewModel.key
  cryptoScore.value = viewModel.score
  cryptoVerificationStatus.value = viewModel.verificationStatus
  cryptoDecryptedResult.value = viewModel.decryptedResult
    ? String(viewModel.decryptedResult)
    : ''
  cryptoProcessTrace.value = Array.isArray(viewModel.processTrace) ? viewModel.processTrace : []
  cryptoSteps.value = (viewModel.steps || []).map((s) => ({
    name: s.name,
    data: s.data,
    done: true
  }))
  cryptoResult.value = viewModel.finalResult
  cryptoTotalTime.value = String(viewModel.totalDuration ?? '')
}

function onCryptoFlowComplete() {
  // Trace 播完后结果已在面板内展示；此处保留钩子便于后续扩展
}

function buildOfflineViewModel(traj = currentTrajectory.value) {
  const isPass = traj?.category === 'pass'
  const dataSource = isPass ? CRYPTO_DATA : CRYPTO_DATA_ABNORMAL
  const finalResult = isPass ? 1 : 0
  dataSource.steps[5].data[2].value = finalResult.toString()

  const category = traj?.category
  const reasonCodes = category === 'time'
    ? ['TIME_DEVIATION']
    : category === 'spatial'
      ? ['DEVIATION_TOO_LARGE']
      : []
  const status = isPass ? '通过' : '不通过'

  return {
    mode: 'offline',
    key: `${dataSource.key} · 离线演示`,
    steps: dataSource.steps.map((s) => ({ name: s.name, data: s.data })),
    processTrace: buildSyntheticProcessTrace({
      x1: '120',
      x2: '260',
      x3: category === 'time' ? '480' : '18',
      x4: category === 'time' ? '900' : '45',
      functionDef: 'f = verify(x)',
      decrypted: String(finalResult),
      thresholdK: thresholdK.value,
      verificationStatus: status,
      score: isPass ? 100 : 40,
      reasonCodes,
      anomalyDesc: traj?.anomalyDesc || null
    }),
    finalResult,
    verificationStatus: status,
    score: isPass ? 100 : 40,
    totalDuration: dataSource.totalDuration,
    decryptedResult: String(finalResult)
  }
}

async function animateCryptoProcess(traj = currentTrajectory.value) {
  const sourceTrajectory = currentTrajectory.value
  if (!traj) {
    playCryptoViewModel(buildOfflineViewModel())
    return
  }

  cryptoMode.value = 'loading'
  cryptoKey.value = '请求 /api/ptmoc/verify ...'
  cryptoSteps.value = []
  cryptoProcessTrace.value = []
  cryptoResult.value = null
  cryptoTotalTime.value = ''
  cryptoScore.value = null
  cryptoVerificationStatus.value = ''
  cryptoDecryptedResult.value = ''

  try {
    const response = await verifyTrajectory({
      userTrajectory: traj.userTrajectory,
      referenceTrajectory: traj.referenceTrajectory,
      thresholdK: thresholdK.value,
      timeAnomaly: !!traj.timeAnomaly,
      anomalyDesc: traj.anomalyDesc || null
    })
    if (currentTrajectory.value !== sourceTrajectory) return
    playCryptoViewModel(buildCryptoViewModel(response))
  } catch (err) {
    if (currentTrajectory.value !== sourceTrajectory) return
    console.warn('[PTMOC] backend unavailable, using offline CRYPTO_DATA:', err)
    const offline = buildOfflineViewModel(traj)
    offline.key = `离线兜底（后端不可用: ${err?.message || err}）`
    playCryptoViewModel(offline)
  }
}

// ========== 返回主页 ==========
function goBackToMain() {
  if (isTraveling.value) return
  // 清理定时器
  if (pathAnimTimeout) { cancelAnimationFrame(pathAnimTimeout); pathAnimTimeout = null }
  if (travelTimerInterval) { clearInterval(travelTimerInterval); travelTimerInterval = null }
  // 销毁travelMap（v-if会销毁DOM）
  if (travelMap) { travelMap.remove(); travelMap = null }
  currentPage.value = 'main'
  // watcher + initMainMap 会处理地图重建和轨迹绘制
}

// ========== 初始化 ==========
onMounted(() => {
  // 登录页不需要初始化地图，watch currentPage 切换到 main 时再初始化
})

// 页面切换时重新初始化地图
watch(currentPage, (val) => {
  if (val === 'main') {
    nextTick(() => {
      if (!mainMap) initMainMap()
      else {
        setTimeout(() => {
          mainMap.invalidateSize()
          if (currentTrajectory.value) updateMainMap()
          else refreshEndpointMarkers()
        }, 100)
      }
    })
  } else if (val === 'debug') {
    nextTick(() => {
      debugSelectedIndex.value = -1
      debugSelectedCoord.value = ''
      initDebugMap()
    })
  }
})
</script>

<style scoped>
.app-container {
  min-height: 100vh;
  background: #f0f2f5;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

/* ============ 登录页 ============ */
.page-login {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
}
.login-card {
  background: white;
  border-radius: 12px;
  width: 400px;
  box-shadow: 0 8px 40px rgba(0,0,0,0.3);
  overflow: hidden;
}
.login-header {
  background: linear-gradient(135deg, #1a1a2e, #16213e);
  color: white;
  padding: 24px 20px;
  text-align: center;
}
.login-header h1 { margin: 0 0 4px 0; font-size: 18px; }
.login-header p { margin: 0; font-size: 11px; opacity: 0.7; font-family: monospace; }
.login-body { padding: 24px 20px; }
.form-group { margin-bottom: 14px; }
.form-group label { display: block; font-size: 12px; color: #666; margin-bottom: 4px; font-weight: 500; }
.form-group input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  font-size: 14px;
  box-sizing: border-box;
  transition: border-color 0.3s;
}
.form-group input:focus { border-color: #1890ff; outline: none; box-shadow: 0 0 0 2px rgba(24,144,255,0.1); }
.login-error { font-size: 12px; color: #ff4d4f; margin-bottom: 10px; padding: 6px 10px; background: #fff2f0; border-radius: 4px; }
.login-btn {
  width: 100%;
  padding: 10px;
  background: linear-gradient(135deg, #1890ff, #096dd9);
  color: white;
  border: none;
  border-radius: 6px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
}
.login-btn:hover { box-shadow: 0 4px 12px rgba(24,144,255,0.4); }
.login-switch { text-align: center; margin-top: 14px; font-size: 13px; color: #999; }
.login-switch a { color: #1890ff; cursor: pointer; text-decoration: none; }
.login-switch a:hover { text-decoration: underline; }

/* ============ 通用头部 ============ */
.app-header {
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
  color: white;
  padding: 10px 24px;
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
}
.app-header h1 { margin: 0; font-size: 18px; font-weight: 600; }
.subtitle { font-size: 11px; opacity: 0.7; font-family: monospace; }
.header-right { margin-left: auto; display: flex; align-items: center; gap: 10px; }
.user-info { font-size: 13px; opacity: 0.9; }
.logout-btn {
  padding: 4px 12px;
  background: rgba(255,255,255,0.15);
  border: 1px solid rgba(255,255,255,0.3);
  border-radius: 4px;
  color: white;
  cursor: pointer;
  font-size: 12px;
}
.logout-btn:hover { background: rgba(255,255,255,0.25); }

.toolbar {
  background: white;
  padding: 10px 20px;
  display: flex;
  align-items: center;
  gap: 20px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.08);
  flex-shrink: 0;
  flex-wrap: wrap;
}
.toolbar-goal1 {
  align-items: flex-start;
}
.toolbar-left { display: flex; gap: 12px; align-items: flex-end; flex-wrap: wrap; }
.toolbar-demo {
  display: flex;
  gap: 12px;
  align-items: flex-end;
  margin-left: auto;
  padding-left: 12px;
  border-left: 1px solid #f0f0f0;
}
.plan-btn {
  height: 32px;
  padding: 0 16px;
  border: none;
  border-radius: 4px;
  background: #13c2c2;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}
.plan-btn:hover:not(:disabled) { background: #08979c; }
.plan-btn:disabled { background: #d9d9d9; cursor: not-allowed; }
.plan-hint {
  font-size: 12px;
  max-width: 420px;
  line-height: 1.4;
}
.plan-hint.info { color: #595959; }
.plan-hint.ok { color: #389e0d; }
.plan-hint.warn { color: #cf1322; }
.legend-dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  margin: 0 4px 0 8px;
  vertical-align: middle;
}
.legend-dot.legend-start { background: #52c41a; }
.legend-dot.legend-end { background: #ff4d4f; }
.legend-line.legend-alt {
  background: #8c8c8c;
  opacity: 0.7;
}
.main-workspace {
  display: flex;
  gap: 12px;
  flex: 1;
  min-height: 0;
  padding: 12px 16px 16px;
  box-sizing: border-box;
}
.main-workspace .map-full {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  margin: 0;
}
.route-side {
  width: 300px;
  flex-shrink: 0;
  overflow-y: auto;
  padding: 4px 2px;
}
.route-loading {
  font-size: 13px;
  color: #1677ff;
  margin-bottom: 8px;
}
.form-row { display: flex; flex-direction: column; gap: 2px; }
.form-row label { font-size: 11px; color: #999; font-weight: 500; }
.form-row select {
  padding: 5px 8px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 13px;
  background: white;
  cursor: pointer;
  min-width: 110px;
}
.form-row select:disabled { background: #f5f5f5; cursor: not-allowed; }
.toolbar-center { display: flex; align-items: center; gap: 14px; font-size: 13px; color: #555; }
.traj-info { font-size: 12px; color: #666; }
.traj-info b { color: #333; }
.toolbar-right { margin-left: auto; }

.verify-btn {
  padding: 8px 24px;
  background: linear-gradient(135deg, #1890ff, #096dd9);
  color: white;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
}
.verify-btn:hover:not(:disabled) { box-shadow: 0 4px 12px rgba(24,144,255,0.4); }
.verify-btn:disabled { background: #d9d9d9; cursor: not-allowed; }

.category-tag { font-size: 12px; padding: 2px 10px; border-radius: 4px; font-weight: 500; }
.category-tag.cat-pass { background: #f6ffed; color: #52c41a; border: 1px solid #b7eb8f; }
.category-tag.cat-spatial { background: #fff2f0; color: #cf1322; border: 1px solid #ffa39e; }
.category-tag.cat-time { background: #fff7e6; color: #fa8c16; border: 1px solid #ffd591; }

.map-title {
  padding: 10px 14px;
  font-size: 14px;
  font-weight: 600;
  color: #1a1a2e;
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}
.map-legend { font-size: 11px; font-weight: normal; color: #888; display: flex; align-items: center; gap: 8px; }
.legend-line { display: inline-block; width: 20px; height: 3px; border-radius: 2px; vertical-align: middle; margin-right: 2px; }
.legend-user { background: #1890ff; }
.legend-ref { background: #006400; border-top: 2px dashed #006400; height: 0; }
.map-container { flex: 1; min-height: 0; }
.custom-marker { background: none !important; border: none !important; }
.car-marker { background: none !important; border: none !important; }

/* ============ 主页 ============ */
.page-main { display: flex; flex-direction: column; height: 100vh; }
.map-full {
  flex: 1;
  background: white;
  border-radius: 8px;
  margin: 8px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.08);
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

/* ============ 行程页 ============ */
.page-travel { display: flex; flex-direction: column; height: 100vh; }
.travel-header { flex-shrink: 0; }
.spatial-notice {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 10px 14px;
  background: #fff7e6;
  color: #874d00;
  border-bottom: 1px solid #ffd591;
  font-size: 13px;
  line-height: 1.6;
  flex-shrink: 0;
}
.spatial-notice strong { display: block; }
.spatial-notice button {
  margin-left: auto;
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font-size: 22px;
  padding: 0 4px;
}
.back-btn {
  padding: 5px 12px;
  background: rgba(255,255,255,0.15);
  border: 1px solid rgba(255,255,255,0.3);
  border-radius: 4px;
  color: white;
  cursor: pointer;
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 4px;
}
.back-btn:hover { background: rgba(255,255,255,0.25); }
.back-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.back-arrow { font-size: 14px; font-weight: bold; }

.travel-layout { flex: 1; display: flex; min-height: 0; padding: 8px; gap: 8px; }
.travel-map-area {
  flex: 1;
  background: white;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.08);
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}
.travel-sidebar {
  width: 520px;
  flex-shrink: 0;
  background: white;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.08);
  overflow-y: auto;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* 计时器 */
.timer-section {
  text-align: center;
  padding: 16px;
  background: #1a1a2e;
  border-radius: 8px;
  color: white;
}
.timer-label { font-size: 12px; opacity: 0.7; margin-bottom: 4px; }
.timer-display { font-size: 36px; font-weight: 700; font-family: monospace; letter-spacing: 2px; }
.timer-hint { font-size: 10px; opacity: 0.5; margin-top: 4px; }

/* PTMOC 密码流程：样式由 CryptoFlowPanel 自带 */
.crypto-section {
  background: transparent;
  border: none;
  border-radius: 0;
  padding: 0;
  flex: 1;
  min-height: 0;
}
.crypto-mode-badge {
  display: inline-block;
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
  margin-bottom: 10px;
  font-weight: 600;
}
.crypto-mode-badge.online {
  background: #f6ffed;
  color: #389e0d;
  border: 1px solid #b7eb8f;
}
.crypto-mode-badge.offline {
  background: #fff7e6;
  color: #d46b08;
  border: 1px solid #ffd591;
}
.crypto-mode-badge.loading {
  background: #e6f4ff;
  color: #1677ff;
  border: 1px solid #91caff;
}
.crypto-loading {
  font-size: 13px;
  color: #1677ff;
  margin-bottom: 4px;
}
.crypto-decrypt-line {
  margin-top: 8px;
  font-size: 12px;
  color: #666;
  word-break: break-all;
}
.crypto-section h4 {
  font-size: 14px;
  color: #1a1a2e;
  margin: 0 0 12px 0;
  padding-bottom: 6px;
  border-bottom: 2px solid #1890ff;
}
.crypto-key-display {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 8px 10px;
  background: #e6f7ff;
  border-radius: 6px;
  margin-bottom: 10px;
  border: 1px solid #91d5ff;
}
.key-label { font-size: 12px; color: #1890ff; font-weight: 600; white-space: nowrap; }
.key-value { font-size: 10px; color: #333; font-family: 'Courier New', monospace; word-break: break-all; }
.crypto-content { display: flex; flex-direction: column; gap: 10px; }

.crypto-step {
  background: white;
  border-radius: 6px;
  padding: 10px;
  border: 1px solid #e8e8e8;
}
.crypto-step-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.crypto-step-idx {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #1890ff;
  color: white;
  font-size: 11px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.crypto-step-name { font-size: 13px; font-weight: 600; color: #333; flex: 1; }
.crypto-step-status { font-size: 13px; color: #d9d9d9; }
.crypto-step-status.done { color: #52c41a; font-weight: 700; }

.crypto-step-detail {
  margin-top: 6px;
  padding-top: 6px;
  border-top: 1px solid #f0f0f0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.crypto-data-row {
  display: flex;
  font-size: 11px;
  gap: 4px;
  align-items: baseline;
}
.data-label { color: #999; white-space: nowrap; min-width: 80px; }
.data-value {
  color: #333;
  font-family: 'Courier New', monospace;
  font-size: 10px;
  word-break: break-all;
  line-height: 1.4;
}

.crypto-result {
  margin-top: 8px;
  padding: 12px;
  border-radius: 8px;
  text-align: center;
}
.crypto-result-label { font-size: 12px; color: #999; margin-bottom: 4px; }
.crypto-result-value {
  font-size: 32px;
  font-weight: 700;
  font-family: monospace;
}
.crypto-result-value.pass { color: #52c41a; }
.crypto-result-value.fail { color: #ff4d4f; }
.result-text { font-size: 14px; font-weight: 500; }

.crypto-total-time {
  display: flex;
  justify-content: space-between;
  padding: 8px 12px;
  background: white;
  border-radius: 6px;
  border: 1px solid #e8e8e8;
}
.total-label { font-size: 13px; color: #999; }
.total-value { font-size: 13px; color: #1890ff; font-weight: 600; font-family: monospace; }

/* ============ 弹窗 ============ */
.alert-overlay {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0,0,0,0.5);
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}
.alert-dialog {
  background: white;
  border-radius: 12px;
  width: 420px;
  box-shadow: 0 8px 40px rgba(0,0,0,0.2);
  padding: 32px 24px;
  text-align: center;
}
.alert-icon {
  font-size: 48px;
  margin-bottom: 12px;
}
.alert-success .alert-icon { color: #52c41a; }
.alert-warning .alert-icon { color: #fa8c16; }
.alert-message {
  font-size: 16px;
  font-weight: 500;
  color: #333;
  margin-bottom: 20px;
  line-height: 1.6;
}
.alert-btn {
  padding: 8px 32px;
  background: linear-gradient(135deg, #1890ff, #096dd9);
  color: white;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}
.alert-btn:hover { box-shadow: 0 4px 12px rgba(24,144,255,0.4); }

/* 覆盖层动画 */
.overlay-enter-active, .overlay-leave-active { transition: all 0.3s ease; }
.overlay-enter-from, .overlay-leave-to { opacity: 0; }
.overlay-enter-from .alert-dialog, .overlay-leave-to .alert-dialog { transform: scale(0.95); }

/* ============ 调试页 ============ */
.debug-entry { text-align: center; margin-top: 10px; font-size: 12px; }
.debug-entry a { color: #999; cursor: pointer; text-decoration: none; }
.debug-entry a:hover { color: #fa8c16; }

.page-debug { display: flex; flex-direction: column; height: 100vh; }
.debug-layout { flex: 1; display: flex; min-height: 0; padding: 8px; gap: 8px; }
.debug-map-area {
  flex: 1;
  background: white;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.08);
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}
.legend-fork { display: inline-block; width: 12px; height: 12px; border-radius: 50%; background: #fa8c16; vertical-align: middle; margin-right: 2px; }
.debug-sidebar {
  width: 320px;
  flex-shrink: 0;
  background: white;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.08);
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.debug-info-section {
  background: #fafafa;
  border-radius: 8px;
  padding: 14px;
  border: 1px solid #f0f0f0;
}
.debug-info-section h4 { font-size: 14px; color: #1a1a2e; margin: 0 0 10px 0; padding-bottom: 6px; border-bottom: 2px solid #fa8c16; }
.debug-selected-info { display: flex; flex-direction: column; gap: 6px; }
.debug-data-row { display: flex; font-size: 12px; gap: 4px; align-items: baseline; }
.debug-data-row .data-label { color: #999; white-space: nowrap; min-width: 80px; }
.debug-data-row .data-value { color: #333; font-family: 'Courier New', monospace; word-break: break-all; }
.debug-hint { font-size: 13px; color: #888; line-height: 1.6; }
.debug-hint b { color: #fa8c16; }
.debug-fork-list { max-height: 300px; overflow-y: auto; display: flex; flex-direction: column; gap: 4px; }
.debug-fork-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 10px;
  background: white;
  border: 1px solid #e8e8e8;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}
.debug-fork-item:hover { border-color: #fa8c16; background: #fff7e6; }
.debug-fork-item.selected { border-color: #fa8c16; background: #fff7e6; box-shadow: 0 0 0 2px rgba(250,140,22,0.2); }
.fork-idx { font-weight: 600; color: #333; }
.fork-dist { color: #888; font-size: 11px; }
.debug-confirm-btn {
  width: 100%;
  padding: 10px;
  background: linear-gradient(135deg, #fa8c16, #d48806);
  color: white;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
}
.debug-confirm-btn:hover:not(:disabled) { box-shadow: 0 4px 12px rgba(250,140,22,0.4); }
.debug-confirm-btn:disabled { background: #d9d9d9; cursor: not-allowed; }
.debug-idx-marker { background: none !important; border: none !important; }
.debug-fork-marker { background: none !important; border: none !important; }
</style>
