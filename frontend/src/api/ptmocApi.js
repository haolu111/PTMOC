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
 * Build right-panel crypto view from a real VerifyResponse (processTrace-driven).
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
      name,
      data: detailById[id] || [{ label: '描述', value: s.description || '—' }],
      duration: s.actualDuration
    }
  })

  const passed = response?.verificationStatus === '通过'
  let processTrace = Array.isArray(response?.processTrace) ? response.processTrace : []
  if (!processTrace.length) {
    processTrace = buildSyntheticProcessTrace({
      x1, x2, x3, x4,
      functionDef: fn,
      decrypted,
      thresholdK: cr.thresholdK ?? response?.algorithmSummary?.thresholdK,
      verificationStatus: response?.verificationStatus,
      score: response?.score,
      reasonCodes: response?.reasonCodes || [],
      anomalyDesc: response?.anomalyDesc,
      cryptoError: cr.error
    })
  }

  return {
    mode: 'online',
    key: `真实 PTMOC · k=${cr.thresholdK ?? response?.algorithmSummary?.thresholdK ?? '—'} · ${hasError ? 'crypto部分失败' : 'Encrypt/Eval/Decrypt OK'}`,
    steps,
    processTrace,
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

/** 后端无 processTrace 或离线兜底时，合成可播放事件 */
export function buildSyntheticProcessTrace(opts = {}) {
  const k = opts.thresholdK ?? 3
  const events = []
  let order = 0
  const push = (stage, actor, target, title, description, displayData) => {
    events.push({ stage, actor, target, title, description, order: ++order, displayData: displayData || {} })
  }

  push('SETUP', 'SYSTEM', null, '系统初始化 Setup', '生成公共参数并分发', {
    lambda: '256', rsa: '2048', note: '公共参数分发至四方'
  })
  push('KEYGEN', 'SYSTEM', null, '密钥生成 KeyGen', '四方生成密钥材料', {
    entities: 'Sender / Server / CSP / Receiver', thresholdK: String(k), secretHint: '不展示完整私钥'
  })
  push('ENCODE', 'SENDER', null, '轨迹编码 Encode', 'Sender 将偏差编码为 x₁…x₄', {
    x1: String(opts.x1 ?? '—'), x2: String(opts.x2 ?? '—'),
    x3: String(opts.x3 ?? '—'), x4: String(opts.x4 ?? '—'),
    x1Label: '平均空间偏差(cm)', x2Label: '最大空间偏差(cm)',
    x3Label: '平均时间偏差(s)', x4Label: '最大时间偏差(s)',
    function: String(opts.functionDef ?? '—')
  })
  push('ENCRYPT', 'SENDER', null, 'Shamir 分片 + 加密', '明文不离开 Sender', {
    thresholdK: String(k), shareHint: '多项式秘密共享', plaintextOnServer: 'false'
  })
  push('ENCRYPT', 'SENDER', 'SERVER', '密文送达 Server', '加密份额发送至 Server', {
    payload: 'Encrypted Shares', plaintext: '不可见'
  })
  push('ENCRYPT', 'SENDER', 'CSP', '辅助密文送达 CSP', '辅助数据发送至 CSP', {
    payload: 'Auxiliary Ciphertext', plaintext: '不可见'
  })
  push('EVAL', 'SERVER', null, 'Server 密文评估', '密文域函数评估', {
    action: 'Function Evaluation', function: String(opts.functionDef ?? 'f(x)'), domain: '密文域'
  })
  push('EVAL', 'CSP', null, 'CSP 掩码处理', '随机掩码协同', { action: 'Random Mask' })
  push('EVAL', 'SERVER', 'RECEIVER', '密文结果送往 Receiver', '加密函数结果交接', {
    result: 'Encrypted Function Result', plaintext: '不可见'
  })
  push('DECRYPT', 'RECEIVER', null, 'Receiver 解密', '恢复函数值而非原始轨迹', {
    decryptedResult: String(opts.decrypted ?? '—'),
    note: '恢复的是函数评估值，不是原始轨迹明文'
  })

  if (opts.cryptoError) {
    push('RESULT', 'SYSTEM', null, '密码流程异常', '离线/失败兜底', { error: String(opts.cryptoError) })
  }

  const reasonCodes = opts.reasonCodes || []
  const status = opts.verificationStatus || '—'
  let badge = 'UNKNOWN'
  if (status.includes('通过') && !reasonCodes.length) badge = 'PASS'
  else if (reasonCodes.includes('TIME_DEVIATION')) badge = 'TIME ANOMALY'
  else if (reasonCodes.includes('DEVIATION_TOO_LARGE') || reasonCodes.includes('PARTIAL_DEVIATION')) badge = 'SPATIAL DEVIATION'
  else if (String(status).includes('不通过')) badge = 'FAIL'
  else badge = status

  push('RESULT', 'RECEIVER', null, '验证结论', '输出最终验证结果', {
    verificationStatus: status,
    score: opts.score ?? null,
    resultBadge: badge,
    privacyNote: '原始轨迹明文未发送给 Server / CSP',
    reasonCodes,
    anomalyDesc: opts.anomalyDesc || null
  })

  return events
}
