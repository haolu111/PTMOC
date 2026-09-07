/**
 * PTMOC backend API client.
 * Prefers real /api/ptmoc/verify; callers should fall back to offline CRYPTO_DATA on failure.
 */

const DEFAULT_BASE = (typeof import.meta !== 'undefined' && import.meta.env && import.meta.env.VITE_PTMOC_API_BASE) || ''

function apiUrl(path) {
  if (DEFAULT_BASE) {
    return `${DEFAULT_BASE.replace(/\/$/, '')}${path}`
  }
  return path
}

export async function checkHealth(timeoutMs = 2500) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeoutMs)
  try {
    const res = await fetch(apiUrl('/api/ptmoc/health'), {
      method: 'GET',
      signal: controller.signal
    })
    if (!res.ok) throw new Error(`health ${res.status}`)
    return await res.json()
  } finally {
    clearTimeout(timer)
  }
}

/**
 * @param {object} payload
 * @param {Array<{lat:number,lng:number,timestamp:number,time?:string}>} payload.userTrajectory
 * @param {Array<{lat:number,lng:number,timestamp:number,time?:string}>} payload.referenceTrajectory
 * @param {number} [payload.thresholdK=3]
 * @param {boolean} [payload.timeAnomaly=false]
 * @param {string|null} [payload.anomalyDesc=null]
 * @param {number} [timeoutMs=120000]
 */
function normalizePoints(points = []) {
  return points.map((p) => ({
    lat: Number(p.lat),
    lng: Number(p.lng),
    timestamp: Math.round(Number(p.timestamp)),
    time: p.time ?? null
  }))
}

export async function verifyTrajectory(payload, timeoutMs = 120000) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeoutMs)
  try {
    const res = await fetch(apiUrl('/api/ptmoc/verify'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        userTrajectory: normalizePoints(payload.userTrajectory),
        referenceTrajectory: normalizePoints(payload.referenceTrajectory),
        thresholdK: payload.thresholdK ?? 3,
        timeAnomaly: !!payload.timeAnomaly,
        anomalyDesc: payload.anomalyDesc ?? null
      }),
      signal: controller.signal
    })
    if (!res.ok) {
      const text = await res.text().catch(() => '')
      throw new Error(`verify failed: ${res.status} ${text}`)
    }
    return await res.json()
  } finally {
    clearTimeout(timer)
  }
}

/** Truncate long crypto values for UI display. */
export function shortHex(value, head = 8, tail = 4) {
  if (value == null) return ''
  const s = String(value)
  if (s.length <= head + tail + 3) return s
  return `${s.slice(0, head)}...${s.slice(-tail)}`
}

/**
 * Build right-panel crypto steps from a real VerifyResponse.
 */
export function buildCryptoViewModel(response) {
  const cr = response?.cryptoResult && typeof response.cryptoResult === 'object'
    ? response.cryptoResult
    : {}
  const hasError = !!cr.error
  const stepsMeta = Array.isArray(response?.steps) ? response.steps : []

  const x1 = cr.x1 ?? '—'
  const x2 = cr.x2 ?? '—'
  const x3 = cr.x3 ?? '—'
  const x4 = cr.x4 ?? '—'
  const decrypted = cr.decryptedResult ?? '—'
  const fn = cr.functionDefinition ?? '—'

  const detailById = {
    init: [
      { label: '安全参数 λ', value: '256 bits' },
      { label: 'RSA', value: '2048' },
      { label: '来源', value: '真实后端 Setup' }
    ],
    keygen: [
      { label: '实体', value: 'Sender / Server / CSP / Receiver' },
      { label: '门限 k', value: String(cr.thresholdK ?? response?.algorithmSummary?.thresholdK ?? '—') },
      { label: '来源', value: '真实后端 KeyGen' }
    ],
    encode: [
      { label: 'x₁ 平均空间偏差(cm)', value: String(x1) },
      { label: 'x₂ 最大空间偏差(cm)', value: String(x2) },
      { label: 'x₃ 平均时间偏差(s)', value: String(x3) },
      { label: 'x₄ 最大时间偏差(s)', value: String(x4) }
    ],
    encrypt: [
      { label: '编码消息', value: shortHex(cr.encodedMessages || `${x1},${x2},${x3},${x4}`, 24, 8) },
      { label: '门限共享', value: `k=${cr.thresholdK ?? '—'}` },
      { label: '状态', value: hasError ? `失败: ${cr.error}` : 'Encrypt 完成' }
    ],
    evaluate: [
      { label: '验证函数 f', value: String(fn) },
      { label: '评估域', value: '密文域 (Server + CSP)' },
      { label: '状态', value: hasError ? `失败: ${cr.error}` : 'Eval 完成' }
    ],
    decrypt: [
      { label: '解密结果 f(...)', value: String(decrypted) },
      { label: '状态', value: hasError ? `失败: ${cr.error}` : 'Decrypt 完成' }
    ],
    verify: [
      { label: '验证状态', value: response?.verificationStatus ?? '—' },
      { label: '综合评分', value: String(response?.score ?? '—') },
      { label: '平均空间偏差(m)', value: String(response?.metrics?.avgDeviation ?? '—') },
      { label: '平均时间偏差(min)', value: String(response?.metrics?.avgTimeDeviation ?? '—') }
    ]
  }

  const defaultOrder = ['init', 'keygen', 'encode', 'encrypt', 'evaluate', 'decrypt', 'verify']
  const ordered = stepsMeta.length
    ? stepsMeta
    : defaultOrder.map((id) => ({ id, name: id }))

  const steps = ordered.map((s) => {
    const id = s.id || s.name
    const name = s.name || id
    return {
      id,
      name: name.includes('PTMOC') || name.includes('系统') || name.includes('密钥') || name.includes('消息') || name.includes('加密') || name.includes('评估') || name.includes('解密') || name.includes('验证')
        ? name
        : name,
      data: detailById[id] || [{ label: '描述', value: s.description || '—' }],
      duration: s.actualDuration
    }
  })

  const passed = response?.verificationStatus === '通过'
  return {
    mode: 'online',
    key: `真实 PTMOC · k=${cr.thresholdK ?? response?.algorithmSummary?.thresholdK ?? '—'} · ${hasError ? 'crypto部分失败' : 'Encrypt/Eval/Decrypt OK'}`,
    steps,
    finalResult: passed ? 1 : 0,
    verificationStatus: response?.verificationStatus ?? '—',
    score: response?.score ?? null,
    totalDuration: response?.totalDuration ?? 0,
    decryptedResult: decrypted,
    reasonCodes: response?.reasonCodes || [],
    reasonDetails: response?.reasonDetails || [],
    cryptoError: cr.error || null
  }
}
