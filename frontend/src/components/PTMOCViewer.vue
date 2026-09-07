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

      <div class="toolbar">
        <div class="toolbar-left">
          <div class="form-row">
            <label>起点</label>
            <select v-model="selectedStart" disabled>
              <option>人民广场</option>
            </select>
          </div>
          <div class="form-row">
            <label>终点</label>
            <select v-model="selectedEnd" @change="onEndChange">
              <option value="">请选择终点</option>
              <option v-for="e in endPoints" :key="e" :value="e">{{ e }}</option>
            </select>
          </div>
          <div class="form-row">
            <label>阈值 k</label>
            <select v-model.number="thresholdK">
              <option :value="2">2</option>
              <option :value="3">3</option>
              <option :value="4">4</option>
              <option :value="5">5</option>
            </select>
          </div>
        </div>

        <div class="toolbar-center" v-if="currentTrajectory">
          <span class="category-tag" :class="'cat-' + currentTrajectory.category">
            {{ currentTrajectory.label }}
          </span>
          <span class="traj-info">距离: <b>{{ currentTrajectory.distance }}km</b></span>
        </div>

        <div class="toolbar-right">
          <button class="verify-btn" :disabled="!canStart || isTraveling" @click="startTravel">
            {{ isTraveling ? '行程中...' : '开始行程' }}
          </button>
        </div>
      </div>

      <div class="map-full">
        <div class="map-title">
          参考轨迹
          <span class="map-legend">
            <span class="legend-line legend-ref"></span>参考轨迹
          </span>
        </div>
        <div class="map-container" ref="mapRef"></div>
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
              <span class="legend-line legend-user"></span>用户轨迹
            </span>
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

          <!-- PTMOC加密验证过程 -->
          <div class="crypto-section" v-if="showCryptoProcess">
            <h4>PTMOC 加密验证过程</h4>
            <div class="crypto-mode-badge" :class="cryptoMode">
              {{ cryptoMode === 'online' ? '真实后端' : cryptoMode === 'loading' ? '正在调用后端...' : '离线演示兜底' }}
            </div>
            <div class="crypto-key-display">
              <span class="key-label">执行摘要</span>
              <span class="key-value">{{ cryptoKey }}</span>
            </div>
            <div class="crypto-content">
              <div v-if="cryptoMode === 'loading'" class="crypto-loading">正在执行真实 PTMOC Setup → Decrypt...</div>
              <div class="crypto-step" v-for="(step, i) in cryptoSteps" :key="i">
                <div class="crypto-step-header">
                  <span class="crypto-step-idx">{{ i + 1 }}</span>
                  <span class="crypto-step-name">{{ step.name }}</span>
                  <span class="crypto-step-status" :class="step.done ? 'done' : ''">
                    {{ step.done ? '✓' : '...' }}
                  </span>
                </div>
                <div class="crypto-step-detail" v-if="step.done && step.data">
                  <div class="crypto-data-row" v-for="(d, j) in step.data" :key="j">
                    <span class="data-label">{{ d.label }}:</span>
                    <span class="data-value">{{ d.value }}</span>
                  </div>
                </div>
              </div>

              <!-- 解密/验证结果 -->
              <div class="crypto-result" v-if="cryptoResult !== null">
                <div class="crypto-result-label">验证结果</div>
                <div class="crypto-result-value" :class="cryptoResult === 1 ? 'pass' : 'fail'">
                  {{ cryptoVerificationStatus || (cryptoResult === 1 ? '通过' : '不通过') }}
                  <span class="result-text" v-if="cryptoScore !== null">Score: {{ cryptoScore }}</span>
                </div>
                <div class="crypto-decrypt-line" v-if="cryptoDecryptedResult">
                  密文评估解密值: {{ cryptoDecryptedResult }}
                </div>
              </div>

              <!-- 总耗时 -->
              <div class="crypto-total-time" v-if="cryptoTotalTime">
                <span class="total-label">总耗时</span>
                <span class="total-value">{{ cryptoTotalTime }}ms</span>
              </div>
            </div>
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
import { getEndPointsForStart, getRouteByStartEnd } from '../data/trajectoryData.js'
import { ROUTE_COORDS } from '../data/trajectoryData.js'
import { verifyTrajectory, buildCryptoViewModel } from '../api/ptmocApi.js'

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
// 将轨迹点数组从WGS-84转为GCJ-02
function convertPts(pts) {
  return pts.map(p => { const c = wgs84ToGcj02(p.lat, p.lng); return { ...p, lat: c.lat, lng: c.lng } })
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
const selectedStart = ref('人民广场')
const selectedEnd = ref('')
const thresholdK = ref(3)
const currentTrajectory = ref(null)
const isTraveling = ref(false)

const endPoints = computed(() => getEndPointsForStart('人民广场'))
const canStart = computed(() => selectedEnd.value && currentTrajectory.value)

// 地图
const mapRef = ref(null)
const travelMapRef = ref(null)
let mainMap = null
let travelMap = null

function onEndChange() {
  if (!selectedEnd.value) { currentTrajectory.value = null; return }
  const data = getRouteByStartEnd('人民广场', selectedEnd.value)
  if (data) {
    currentTrajectory.value = data
    nextTick(() => updateMainMap())
  }
}

function updateMainMap() {
  if (!mainMap || !currentTrajectory.value) return
  mainMap.invalidateSize()
  mainMap.eachLayer(l => { if (!(l instanceof L.TileLayer)) mainMap.removeLayer(l) })

  const refPts = convertPts(currentTrajectory.value.referenceTrajectory)
  const refLatLngs = refPts.map(p => [p.lat, p.lng])
  L.polyline(refLatLngs, { color: REF_COLOR, weight: 3, opacity: 0.7, dashArray: '10, 7' }).addTo(mainMap)

  L.marker(refLatLngs[0], { icon: createColorIcon('#52c41a') }).addTo(mainMap).bindPopup('起点: 人民广场')
  L.marker(refLatLngs[refLatLngs.length - 1], { icon: createColorIcon('#ff4d4f') }).addTo(mainMap).bindPopup('终点: ' + currentTrajectory.value.end)

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
      }
    }, 200)
  }
}

// ========== 行程页状态 ==========
const travelTimer = ref(0) // 模拟分钟数（1真实秒=1模拟分钟）
const showCryptoProcess = ref(false)
const cryptoSteps = ref([])
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
function startTravel() {
  if (!canStart.value || isTraveling.value) return
  // 清理之前的定时器
  if (pathAnimTimeout) { cancelAnimationFrame(pathAnimTimeout); pathAnimTimeout = null }
  if (travelTimerInterval) { clearInterval(travelTimerInterval); travelTimerInterval = null }

  // 销毁mainMap（v-if切换会销毁DOM，返回时需要重建）
  if (mainMap) { mainMap.remove(); mainMap = null }

  isTraveling.value = true
  currentPage.value = 'travel'
  travelTimer.value = 0
  showCryptoProcess.value = false
  cryptoSteps.value = []
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
    const refPts = convertPts(currentTrajectory.value.referenceTrajectory)
    const refLatLngs = refPts.map(p => [p.lat, p.lng])
    L.polyline(refLatLngs, { color: REF_COLOR, weight: 3, opacity: 0.5, dashArray: '10, 7' }).addTo(travelMap)

    L.marker(refLatLngs[0], { icon: createColorIcon('#52c41a') }).addTo(travelMap).bindPopup('起点')
    L.marker(refLatLngs[refLatLngs.length - 1], { icon: createColorIcon('#ff4d4f') }).addTo(travelMap).bindPopup('终点')

    // 用户轨迹 - 空的，会逐步添加
    userPolyline = L.polyline([], { color: USER_COLOR, weight: 4, opacity: 0.9 }).addTo(travelMap)

    // 小车标记 - 初始在起点
    carMarker = L.marker(refLatLngs[0], { icon: createCarIcon(), zIndexOffset: 1000 }).addTo(travelMap)

    travelMap.fitBounds(L.latLngBounds(refLatLngs), { padding: [50, 50] })
  }
}

function startPathAnimation() {
  const category = currentTrajectory.value.category
  const refPts = convertPts(currentTrajectory.value.referenceTrajectory)

  // ===== 构建实际展示的用户轨迹 =====
  let displayPts = []
  let deviationSplitIndex = -1

  if (category === 'spatial') {
    // 用户选中偏离点索引（来自调试页）
    const forkRefIndex = selectedForkIndex.value >= 0 ? selectedForkIndex.value : 12

    // 获取alt轨迹原始坐标（WGS-84），转GCJ-02
    const altKey = currentTrajectory.value.start + '→' + currentTrajectory.value.end + '_alt'
    const altRawCoords = ROUTE_COORDS[altKey]
    const altPts = altRawCoords ? convertPts(altRawCoords.map(c => ({ lat: c[0], lng: c[1] }))) : []

    // 偏离点前：严格走参考轨迹（0 ~ forkRefIndex，含偏离点本身）
    // 偏离点后：切换到alt轨迹，平移修正对齐

    // 计算参考轨迹偏离点坐标
    const refForkPt = refPts[forkRefIndex]
    // 在alt轨迹中找到距离refForkPt最近的点
    let nearestAltIdx = forkRefIndex // 默认同索引
    let minDist = Infinity
    if (altPts.length > 0) {
      for (let i = 0; i < altPts.length; i++) {
        const dlat = altPts[i].lat - refForkPt.lat
        const dlng = altPts[i].lng - refForkPt.lng
        const d = dlat * dlat + dlng * dlng
        if (d < minDist) { minDist = d; nearestAltIdx = i }
      }
    }

    // alt轨迹从nearestAltIdx开始，走3个路段（4个点）后触发警告
    const altSegments = 3
    const altEndIdx = Math.min(nearestAltIdx + altSegments + 1, altPts.length)

    // 取出alt路线片段 [nearestAltIdx .. altEndIdx-1]
    const altSlice = altPts.slice(nearestAltIdx, altEndIdx)

    // 平移修正：将altSlice的第一个点对齐到refForkPt
    // 偏移量 = refForkPt - altSlice[0]
    const offsetLat = refForkPt.lat - altSlice[0].lat
    const offsetLng = refForkPt.lng - altSlice[0].lng

    // 应用平移：将整个altSlice平移，使起始点与refForkPt重合
    // 对后续点做渐变修正：越远的点偏移越小（线性衰减到0），
    // 这样在第2-3个点处轨迹自然过渡到alt原始方向
    const correctedAltSlice = altSlice.map((pt, i) => {
      if (i === 0) {
        // 第一个点：完全对齐到refForkPt
        return { lat: refForkPt.lat, lng: refForkPt.lng }
      }
      // 渐变衰减：从1衰减到0，让轨迹逐渐回归到alt原始方向
      const decay = 1 - (i / altSlice.length)
      return {
        lat: pt.lat + offsetLat * decay,
        lng: pt.lng + offsetLng * decay
      }
    })

    displayPts = [
      ...refPts.slice(0, forkRefIndex),      // 参考轨迹 0..forkRefIndex-1
      ...correctedAltSlice                    // 从偏离点开始走alt路线（3个路段）
    ]
    deviationSplitIndex = forkRefIndex  // displayPts中偏移开始的索引
  } else {
    // 时间异常和准确无误：路线相同，用参考轨迹
    displayPts = [...refPts]
  }

  const totalDisplayPoints = displayPts.length

  // ===== 计算每个线段的距离 =====
  function pointDist(p1, p2) {
    const latDiff = p1.lat - p2.lat
    const lngDiff = p1.lng - p2.lng
    return Math.sqrt(latDiff * latDiff + lngDiff * lngDiff) * 111000 // 米
  }

  // 计算总距离和每段累计距离
  let totalDist = 0
  const segDistances = [] // 每段距离
  const cumDistances = [0] // 累计距离（从0开始）
  for (let i = 1; i < totalDisplayPoints; i++) {
    const d = pointDist(displayPts[i - 1], displayPts[i])
    segDistances.push(d)
    totalDist += d
    cumDistances.push(totalDist)
  }

  // 匀速运动：全程约20秒
  const totalDuration = 20000 // 毫秒
  const speed = totalDist / totalDuration // 米/毫秒

  // 时间异常的停止距离位置
  const timeAnomalyStopDist = category === 'time'
    ? cumDistances[Math.floor(totalDisplayPoints * 0.45)]
    : -1

  // ===== 计时器：1真实秒 = 1模拟分钟 =====
  travelTimerInterval = setInterval(() => {
    travelTimer.value++
  }, 1000)

  // ===== 连续动画（requestAnimationFrame） =====
  let animStartTime = null
  let animPausedAt = null // 暂停时已经走过的距离
  let timeAnomalyPaused = false
  let timeAnomalyResumeTime = null
  let animFinished = false

  // 根据已走距离，计算当前应该在哪个位置（插值）
  function getPositionAtDist(dist) {
    if (dist <= 0) return { lat: displayPts[0].lat, lng: displayPts[0].lng, segIdx: 0 }
    if (dist >= totalDist) {
      const last = displayPts[displayPts.length - 1]
      return { lat: last.lat, lng: last.lng, segIdx: displayPts.length - 2 }
    }
    // 找到dist落在哪个线段
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
      segIdx
    }
  }

  // 根据已走距离，生成蓝线的所有点（经过的所有waypoint + 当前插值点）
  function getBlueLinePoints(dist) {
    const pos = getPositionAtDist(dist)
    const pts = []
    // 加上所有已经过的waypoint
    for (let i = 0; i <= pos.segIdx; i++) {
      pts.push([displayPts[i].lat, displayPts[i].lng])
    }
    // 加上当前插值点（如果不在waypoint上）
    const lastWP = displayPts[pos.segIdx]
    if (Math.abs(pos.lat - lastWP.lat) > 1e-8 || Math.abs(pos.lng - lastWP.lng) > 1e-8) {
      pts.push([pos.lat, pos.lng])
    }
    return pts
  }

  function animFrame(timestamp) {
    if (animFinished || travelAlertFired) return

    if (!animStartTime) animStartTime = timestamp

    // 时间异常暂停逻辑
    if (timeAnomalyPaused) {
      if (timestamp >= timeAnomalyResumeTime) {
        timeAnomalyPaused = false
        animStartTime = timestamp - (animPausedAt / speed) // 调整起始时间使动画从暂停位置继续
      } else {
        pathAnimTimeout = requestAnimationFrame(animFrame)
        return
      }
    }

    const elapsed = timestamp - animStartTime
    const currentDist = Math.min(speed * elapsed, totalDist)

    // 获取当前位置
    const pos = getPositionAtDist(currentDist)

    // 更新小车位置
    if (carMarker) {
      carMarker.setLatLng([pos.lat, pos.lng])
    }

    // 更新蓝线
    const bluePts = getBlueLinePoints(currentDist)
    if (bluePts.length >= 2) {
      currentUserLatLngs = bluePts
      userPolyline.setLatLngs(currentUserLatLngs)
    }

    // 地图跟随
    if (travelMap) {
      travelMap.panTo([pos.lat, pos.lng], { animate: false })
    }

    // === 时间异常：到达停止点时暂停5秒后终止并弹窗 ===
    if (category === 'time' && timeAnomalyStopDist > 0 && currentDist >= timeAnomalyStopDist && !timeAnomalyPaused) {
      timeAnomalyPaused = true
      animPausedAt = currentDist
      // 停留5秒后直接终止并弹窗
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

    // === 路线偏移：到达终点后停顿再弹窗 ===
    if (category === 'spatial' && deviationSplitIndex > 0 && currentDist >= totalDist) {
      animFinished = true
      setTimeout(() => {
        travelAlertFired = true
        clearInterval(travelTimerInterval)
        travelTimerInterval = null
        isTraveling.value = false
        showAlert('warning', '路线偏移！检测到行驶路线与参考轨迹不一致，已触发PTMOC验证。')
      }, 1500)
      return
    }

    // === 正常到达终点 ===
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

    // 继续动画
    if (currentDist < totalDist) {
      pathAnimTimeout = requestAnimationFrame(animFrame)
    }
  }

  // 启动连续动画
  pathAnimTimeout = requestAnimationFrame(animFrame)
}

// ========== PTMOC加密验证动画（优先真实后端，失败则离线兜底） ==========
function playCryptoViewModel(viewModel) {
  cryptoMode.value = viewModel.mode
  cryptoKey.value = viewModel.key
  cryptoScore.value = viewModel.score
  cryptoVerificationStatus.value = viewModel.verificationStatus
  cryptoDecryptedResult.value = viewModel.decryptedResult
    ? String(viewModel.decryptedResult)
    : ''

  cryptoSteps.value = viewModel.steps.map((s) => ({
    name: s.name,
    data: null,
    done: false
  }))

  let stepIdx = 0
  const stepInterval = setInterval(() => {
    if (stepIdx >= viewModel.steps.length) {
      clearInterval(stepInterval)
      cryptoResult.value = viewModel.finalResult
      cryptoTotalTime.value = String(viewModel.totalDuration ?? '')
      return
    }
    cryptoSteps.value[stepIdx].done = true
    cryptoSteps.value[stepIdx].data = viewModel.steps[stepIdx].data
    stepIdx++
  }, 600)
}

function buildOfflineViewModel() {
  const isPass = currentTrajectory.value?.category === 'pass'
  const dataSource = isPass ? CRYPTO_DATA : CRYPTO_DATA_ABNORMAL
  const finalResult = isPass ? 1 : 0
  dataSource.steps[5].data[2].value = finalResult.toString()

  return {
    mode: 'offline',
    key: `${dataSource.key} · 离线演示`,
    steps: dataSource.steps.map((s) => ({ name: s.name, data: s.data })),
    finalResult,
    verificationStatus: isPass ? '通过' : '不通过',
    score: isPass ? 100 : 40,
    totalDuration: dataSource.totalDuration,
    decryptedResult: String(finalResult)
  }
}

async function animateCryptoProcess() {
  const traj = currentTrajectory.value
  if (!traj) {
    playCryptoViewModel(buildOfflineViewModel())
    return
  }

  cryptoMode.value = 'loading'
  cryptoKey.value = '请求 /api/ptmoc/verify ...'
  cryptoSteps.value = []
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
    playCryptoViewModel(buildCryptoViewModel(response))
  } catch (err) {
    console.warn('[PTMOC] backend unavailable, using offline CRYPTO_DATA:', err)
    const offline = buildOfflineViewModel()
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
}
.toolbar-left { display: flex; gap: 12px; align-items: flex-end; }
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
  width: 380px;
  flex-shrink: 0;
  background: white;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.08);
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
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

/* PTMOC加密过程 */
.crypto-section {
  background: #fafafa;
  border-radius: 8px;
  padding: 14px;
  border: 1px solid #f0f0f0;
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
