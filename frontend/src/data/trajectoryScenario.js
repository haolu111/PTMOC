/**
 * 根据参考路线 + 场景下拉，动态生成 User / Reference 轨迹。
 * 供「开始行程」使用；不依赖预置 ROUTE_COORDS。
 */

const SCENARIO_META = {
  pass: { category: 'pass', label: '准确无误', timeAnomaly: false },
  spatial: { category: 'spatial', label: '路线偏移', timeAnomaly: false },
  time: { category: 'time', label: '时间异常', timeAnomaly: true }
}

/**
 * @param {object} opts
 * @param {Array<{lat:number,lng:number}>} opts.referencePoints 参考折线点（已是展示坐标系）
 * @param {string} opts.startName
 * @param {string} opts.endName
 * @param {number} [opts.durationSeconds] 参考耗时（秒）
 * @param {'pass'|'spatial'|'time'} opts.scenario
 * @param {'gcj02'|'wgs84'} [opts.coordSystem='gcj02']
 * @param {number} [opts.maxPoints=90] 下采样上限，减轻 PTMOC 输入规模
 */
export function buildScenarioTrajectory(opts) {
  const scenario = opts.scenario || 'pass'
  const meta = SCENARIO_META[scenario] || SCENARIO_META.pass
  const raw = (opts.referencePoints || []).filter(
    (p) => p && Number.isFinite(p.lat) && Number.isFinite(p.lng)
  )
  if (raw.length < 2) {
    throw new Error('参考路线点数不足，无法生成行程轨迹')
  }

  const maxPoints = opts.maxPoints || 90
  const sampled = downsamplePoints(raw, maxPoints)
  const durationSeconds = Math.max(300, Number(opts.durationSeconds) || estimateDurationSeconds(sampled))
  const durationMinutes = durationSeconds / 60
  const baseTime = new Date('2024-01-15T08:00:00').getTime()

  const referenceTrajectory = attachTimestamps(sampled, baseTime, durationMinutes)

  let userTrajectory
  let anomalyDesc = null
  let forkIndex = -1

  if (scenario === 'spatial') {
    const built = buildSpatialUserPath(sampled)
    forkIndex = built.forkIndex
    userTrajectory = attachTimestamps(built.points, baseTime, durationMinutes)
    anomalyDesc = '用户轨迹在中段偏离参考路线，空间偏移过大'
  } else if (scenario === 'time') {
    const timeFactor = 2.0
    userTrajectory = attachTimestamps(sampled, baseTime, durationMinutes * timeFactor)
    anomalyDesc = `用户行驶耗时约为参考路线的 ${timeFactor.toFixed(1)} 倍，存在显著时间偏差`
  } else {
    // 正常：可选轻微噪声，默认与参考一致以保证 PASS 稳定
    userTrajectory = referenceTrajectory.map((p) => ({ ...p }))
    anomalyDesc = null
  }

  return {
    start: opts.startName || '起点',
    end: opts.endName || '终点',
    category: meta.category,
    label: meta.label,
    userTrajectory,
    referenceTrajectory,
    timeAnomaly: meta.timeAnomaly,
    anomalyDesc,
    coordSystem: opts.coordSystem || 'gcj02',
    dynamic: true,
    forkIndex,
    sourceRouteId: opts.sourceRouteId || null
  }
}

export function scenarioOptions() {
  return [
    { value: 'pass', label: '正常' },
    { value: 'spatial', label: '空间异常' },
    { value: 'time', label: '时间异常' }
  ]
}

function downsamplePoints(points, maxPoints) {
  if (points.length <= maxPoints) {
    return points.map((p) => ({ lat: p.lat, lng: p.lng }))
  }
  const out = []
  const n = points.length
  const step = (n - 1) / (maxPoints - 1)
  for (let i = 0; i < maxPoints; i++) {
    const idx = Math.round(i * step)
    const p = points[Math.min(idx, n - 1)]
    out.push({ lat: p.lat, lng: p.lng })
  }
  return out
}

function attachTimestamps(points, baseTime, durationMinutes) {
  const n = points.length
  if (n === 1) {
    return [{ lat: points[0].lat, lng: points[0].lng, timestamp: Math.round(baseTime) }]
  }
  const intervalMs = (durationMinutes * 60 * 1000) / (n - 1)
  return points.map((p, i) => ({
    lat: p.lat,
    lng: p.lng,
    timestamp: Math.round(baseTime + i * intervalMs)
  }))
}

function estimateDurationSeconds(points) {
  let meters = 0
  for (let i = 1; i < points.length; i++) {
    meters += haversineMeters(points[i - 1], points[i])
  }
  // 约 30 km/h
  return Math.max(300, (meters / 1000) * 120)
}

function haversineMeters(a, b) {
  const R = 6371000
  const toRad = (d) => (d * Math.PI) / 180
  const dLat = toRad(b.lat - a.lat)
  const dLng = toRad(b.lng - a.lng)
  const lat1 = toRad(a.lat)
  const lat2 = toRad(b.lat)
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) ** 2
  return 2 * R * Math.asin(Math.min(1, Math.sqrt(h)))
}

/**
 * 中段法向偏移：平滑偏出、持续偏离、平滑回归；首尾贴合参考路线。
 */
function buildSpatialUserPath(refPoints) {
  const n = refPoints.length
  const startIdx = Math.max(1, Math.floor(n * 0.2))
  const endIdx = Math.min(n - 2, Math.floor(n * 0.85))
  const offsetMeters = 500
  const transitionRatio = 0.25 // 偏移区间前后各 25% 用于过渡，中间 50% 保持最大偏移

  // 偏移方向：取中段切线的法向
  const a = refPoints[startIdx]
  const b = refPoints[Math.min(startIdx + 1, n - 1)]
  let dx = b.lng - a.lng
  let dy = b.lat - a.lat
  const len = Math.sqrt(dx * dx + dy * dy) || 1e-9
  dx /= len
  dy /= len
  // 法向 (-dy, dx)
  const nx = -dy
  const ny = dx

  const points = refPoints.map((p, i) => {
    if (i < startIdx || i > endIdx) {
      return { lat: p.lat, lng: p.lng }
    }
    // 平滑抬升到最大偏移，保持一段后再平滑回归
    const t = (i - startIdx) / Math.max(1, endIdx - startIdx)
    const ramp = Math.min(1, t / transitionRatio, (1 - t) / transitionRatio)
    const envelope = ramp * ramp * (3 - 2 * ramp)
    const meters = offsetMeters * envelope
    const dLat = (meters * ny) / 111000
    const dLng = (meters * nx) / (111000 * Math.cos((p.lat * Math.PI) / 180) || 1e-6)
    return { lat: p.lat + dLat, lng: p.lng + dLng }
  })

  return { points, forkIndex: startIdx }
}
