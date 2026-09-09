/**
 * 根据参考路线 + 场景下拉，动态生成 User / Reference 轨迹。
 * 空间异常必须使用「真实道路绕路」（备选规划路线 / 途经点绕路），禁止法向平移假路径。
 */

import { distanceToRouteMeters } from '../utils/routeDeviation.js'

const SCENARIO_META = {
  pass: { category: 'pass', label: '准确无误', timeAnomaly: false },
  spatial: { category: 'spatial', label: '空间异常', timeAnomaly: false },
  time: { category: 'time', label: '时间异常', timeAnomaly: true }
}

/** 认定「明显绕路」的最小最大偏离（米） */
export const MIN_DETOUR_MAX_DEV_METERS = 180

/**
 * @param {object} opts
 * @param {Array<{lat:number,lng:number}>} opts.referencePoints 参考折线点（已是展示坐标系）
 * @param {string} opts.startName
 * @param {string} opts.endName
 * @param {number} [opts.durationSeconds] 参考耗时（秒）
 * @param {'pass'|'spatial'|'time'} opts.scenario
 * @param {'gcj02'|'wgs84'} [opts.coordSystem='gcj02']
 * @param {number} [opts.maxPoints=90] 下采样上限，减轻 PTMOC 输入规模
 * @param {Array<{lat:number,lng:number}>} [opts.detourPoints] 空间异常：真实绕路折线
 * @param {string} [opts.detourDesc] 绕路说明
 * @param {number} [opts.detourDurationSeconds] 绕路耗时（秒），缺省用参考耗时
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
    const detourRaw = (opts.detourPoints || []).filter(
      (p) => p && Number.isFinite(p.lat) && Number.isFinite(p.lng)
    )
    if (detourRaw.length < 2) {
      throw new Error('空间异常需要真实绕路轨迹（请先规划路线，系统会选用备选道路或途经点绕路）')
    }
    const detourSampled = downsamplePoints(detourRaw, maxPoints)
    const detourMinutes = Math.max(
      durationMinutes,
      (Number(opts.detourDurationSeconds) || estimateDurationSeconds(detourSampled)) / 60
    )
    userTrajectory = attachTimestamps(detourSampled, baseTime, detourMinutes)
    forkIndex = findForkIndex(sampled, detourSampled)
    const stats = measurePathDeviation(detourSampled, sampled)
    anomalyDesc =
      opts.detourDesc ||
      `用户未按参考路线行驶，而是走了另一条真实道路绕路（最大偏离约 ${Math.round(stats.max)} m）`
  } else if (scenario === 'time') {
    const timeFactor = 2.0
    userTrajectory = attachTimestamps(sampled, baseTime, durationMinutes * timeFactor)
    anomalyDesc = `用户行驶耗时约为参考路线的 ${timeFactor.toFixed(1)} 倍，存在显著时间偏差`
  } else {
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
    sourceRouteId: opts.sourceRouteId || null,
    detourRouteId: opts.detourRouteId || null,
    detourName: opts.detourName || null
  }
}

export function scenarioOptions() {
  return [
    { value: 'pass', label: '正常' },
    { value: 'spatial', label: '空间异常' },
    { value: 'time', label: '时间异常' }
  ]
}

/**
 * 从已规划的多条路线中，挑一条与参考路线差异最大、且像真实绕路的备选。
 * @returns {{ polyline, routeId, name, durationSeconds, distanceMeters, avgDeviation, maxDeviation } | null}
 */
export function pickDetourFromCandidates(referencePoints, candidateRoutes, selectedRouteId) {
  const ref = (referencePoints || []).filter((p) => p && Number.isFinite(p.lat) && Number.isFinite(p.lng))
  if (ref.length < 2 || !Array.isArray(candidateRoutes)) return null

  let best = null
  for (const route of candidateRoutes) {
    if (!route || route.routeId === selectedRouteId) continue
    const poly = (route.polyline || []).filter((p) => p && Number.isFinite(p.lat) && Number.isFinite(p.lng))
    if (poly.length < 2) continue
    const stats = measurePathDeviation(poly, ref)
    if (stats.max < MIN_DETOUR_MAX_DEV_METERS) continue
    if (!best || stats.max > best.maxDeviation || (stats.max === best.maxDeviation && stats.avg > best.avgDeviation)) {
      best = {
        polyline: poly,
        routeId: route.routeId,
        name: route.name || '备选路线',
        durationSeconds: route.durationSeconds,
        distanceMeters: route.distanceMeters,
        avgDeviation: stats.avg,
        maxDeviation: stats.max
      }
    }
  }
  return best
}

/**
 * 在参考路线中段附近生成一个「旁路途经点」，用于二次规划真实绕路。
 * 优先尝试左右法向；返回若干候选点供调用方依次试探。
 */
export function buildDetourWaypointCandidates(referencePoints, offsetMeters = 1400) {
  const pts = (referencePoints || []).filter((p) => p && Number.isFinite(p.lat) && Number.isFinite(p.lng))
  if (pts.length < 2) return []

  const ratios = [0.35, 0.5, 0.65]
  const offsets = [offsetMeters, offsetMeters * 0.75, offsetMeters * 1.35]
  const candidates = []

  for (const ratio of ratios) {
    const idx = Math.max(1, Math.min(pts.length - 2, Math.floor(pts.length * ratio)))
    const a = pts[idx]
    const b = pts[Math.min(idx + 1, pts.length - 1)]
    let dx = b.lng - a.lng
    let dy = b.lat - a.lat
    const len = Math.sqrt(dx * dx + dy * dy) || 1e-9
    dx /= len
    dy /= len
    // 左右法向
    const normals = [
      { nx: -dy, ny: dx },
      { nx: dy, ny: -dx }
    ]
    for (const meters of offsets) {
      for (const { nx, ny } of normals) {
        const dLat = (meters * ny) / 111000
        const dLng = (meters * nx) / (111000 * Math.cos((a.lat * Math.PI) / 180) || 1e-6)
        candidates.push({
          lat: a.lat + dLat,
          lng: a.lng + dLng,
          label: `途经绕路点(~${Math.round(meters)}m)`
        })
      }
    }
  }
  return candidates
}

/** 抽样测量 path 相对 reference 的平均/最大偏离（米） */
export function measurePathDeviation(pathPoints, referencePoints) {
  const path = pathPoints || []
  const ref = referencePoints || []
  if (!path.length || !ref.length) return { avg: 0, max: 0 }
  const step = Math.max(1, Math.floor(path.length / 24))
  let sum = 0
  let max = 0
  let count = 0
  for (let i = 0; i < path.length; i += step) {
    const d = distanceToRouteMeters(path[i], ref)
    if (!Number.isFinite(d)) continue
    sum += d
    max = Math.max(max, d)
    count++
  }
  // 再测中点附近加密一点，避免漏掉最大偏出段
  const mid = path[Math.floor(path.length / 2)]
  if (mid) {
    const d = distanceToRouteMeters(mid, ref)
    if (Number.isFinite(d)) {
      sum += d
      max = Math.max(max, d)
      count++
    }
  }
  return { avg: count ? sum / count : 0, max }
}

function findForkIndex(refPoints, detourPoints) {
  const n = Math.min(refPoints.length, detourPoints.length)
  for (let i = 0; i < n; i++) {
    const d = haversineMeters(refPoints[i], detourPoints[Math.min(i, detourPoints.length - 1)])
    if (d > 80) return Math.max(0, i - 1)
  }
  // 长度不同时，用偏离参考线的第一个点
  for (let i = 0; i < detourPoints.length; i++) {
    if (distanceToRouteMeters(detourPoints[i], refPoints) > 80) {
      return Math.max(0, Math.floor((i / detourPoints.length) * refPoints.length) - 1)
    }
  }
  return Math.floor(refPoints.length * 0.25)
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
