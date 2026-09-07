/**
 * 地图相关 API（经后端代理，浏览器不会看到高德 Key）
 */

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
