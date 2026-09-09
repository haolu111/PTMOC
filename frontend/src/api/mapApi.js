/**
 * 地图相关 API（经后端代理，浏览器不会看到高德 Key）
 */

import {
  pickDetourFromCandidates,
  buildDetourWaypointCandidates,
  measurePathDeviation,
  MIN_DETOUR_MAX_DEV_METERS
} from '../data/trajectoryScenario.js'

const DEFAULT_BASE = (typeof import.meta !== 'undefined' && import.meta.env && import.meta.env.VITE_PTMOC_API_BASE) || ''

function apiUrl(path) {
  if (DEFAULT_BASE) return `${DEFAULT_BASE.replace(/\/$/, '')}${path}`
  return path
}

export async function checkMapHealth(timeoutMs = 2500) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeoutMs)
  try {
    const res = await fetch(apiUrl('/api/map/health'), { method: 'GET', signal: controller.signal })
    if (!res.ok) throw new Error(`map health ${res.status}`)
    return await res.json()
  } finally {
    clearTimeout(timer)
  }
}

/**
 * @param {string} keywords
 * @param {string} [city='上海']
 * @returns {Promise<Array<{id:string,name:string,district:string,address:string,lng:number,lat:number}>>}
 */
export async function fetchLocationTips(keywords, city = '上海', timeoutMs = 8000) {
  const q = (keywords || '').trim()
  if (q.length < 2) return []

  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeoutMs)
  try {
    const params = new URLSearchParams({ keywords: q, city })
    const res = await fetch(apiUrl(`/api/map/tips?${params.toString()}`), {
      method: 'GET',
      signal: controller.signal
    })
    const data = await res.json().catch(() => ({}))
    if (!res.ok) {
      throw new Error(data.error || `地点搜索失败 (${res.status})`)
    }
    if (!Array.isArray(data)) {
      throw new Error(data.error || '地点搜索返回格式异常')
    }
    return data
  } finally {
    clearTimeout(timer)
  }
}

export function emptyLocation() {
  return { id: '', name: '', address: '', district: '', lng: null, lat: null }
}

export function isLocationSelected(loc) {
  return !!(loc && loc.name && loc.lng != null && loc.lat != null && Number.isFinite(loc.lng) && Number.isFinite(loc.lat))
}

/**
 * 路径规划：返回推荐路线 / 躲避拥堵 / 速度最快 三条路线
 * @param {{lng:number,lat:number}} origin
 * @param {{lng:number,lat:number}} destination
 */
export async function planRoutes(origin, destination, timeoutMs = 20000) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeoutMs)
  try {
    const res = await fetch(apiUrl('/api/map/routes'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        origin: { lng: origin.lng, lat: origin.lat },
        destination: { lng: destination.lng, lat: destination.lat }
      }),
      signal: controller.signal
    })
    const data = await res.json().catch(() => ({}))
    if (!res.ok) {
      throw new Error(data.error || `路径规划失败 (${res.status})`)
    }
    if (!data || !Array.isArray(data.routes)) {
      throw new Error(data.error || '路径规划返回格式异常')
    }
    return data.routes
  } finally {
    clearTimeout(timer)
  }
}

/**
 * 生成贴合真实道路的绕路轨迹：
 * 1) 优先用已规划的其他策略路线（躲避拥堵 / 速度最快）
 * 2) 若差异不够，则用中段旁路途经点：起点→途经点→终点 两次规划后拼接
 *
 * @returns {Promise<{
 *   polyline: Array<{lat:number,lng:number}>,
 *   routeId: string,
 *   name: string,
 *   durationSeconds: number,
 *   distanceMeters: number,
 *   avgDeviation: number,
 *   maxDeviation: number,
 *   source: 'candidate'|'waypoint'
 * }>}
 */
export async function planDetourRoute(opts) {
  const {
    origin,
    destination,
    referencePolyline,
    candidateRoutes = [],
    selectedRouteId = ''
  } = opts || {}

  const fromCandidates = pickDetourFromCandidates(referencePolyline, candidateRoutes, selectedRouteId)
  if (fromCandidates) {
    return { ...fromCandidates, source: 'candidate' }
  }

  const waypoints = buildDetourWaypointCandidates(referencePolyline)
  let lastError = null
  for (const wp of waypoints.slice(0, 6)) {
    try {
      const leg1List = await planRoutes(origin, wp)
      const leg2List = await planRoutes(wp, destination)
      const leg1 = leg1List?.[0]
      const leg2 = leg2List?.[0]
      const p1 = (leg1?.polyline || []).filter((p) => p && Number.isFinite(p.lat) && Number.isFinite(p.lng))
      const p2 = (leg2?.polyline || []).filter((p) => p && Number.isFinite(p.lat) && Number.isFinite(p.lng))
      if (p1.length < 2 || p2.length < 2) continue

      // 去掉接缝重复点
      const stitched = p1.concat(p2.slice(1))
      const stats = measurePathDeviation(stitched, referencePolyline)
      if (stats.max < MIN_DETOUR_MAX_DEV_METERS) continue

      const distanceMeters =
        (Number(leg1.distanceMeters) || 0) + (Number(leg2.distanceMeters) || 0)
      const durationSeconds =
        (Number(leg1.durationSeconds) || 0) + (Number(leg2.durationSeconds) || 0)

      return {
        polyline: stitched,
        routeId: 'route-waypoint-detour',
        name: '途经点绕路',
        durationSeconds,
        distanceMeters,
        avgDeviation: stats.avg,
        maxDeviation: stats.max,
        source: 'waypoint'
      }
    } catch (err) {
      lastError = err
      // 免费 Key 限流时稍等再试下一个途经点
      await new Promise((r) => setTimeout(r, 400))
    }
  }

  throw new Error(
    lastError?.message ||
      '未能规划出与参考路线明显不同的真实绕路，请换一组起终点或稍后再试'
  )
}
