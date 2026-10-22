import { distanceToRouteMeters, SPATIAL_ALERT_THRESHOLD_METERS } from './routeDeviation.js'

export const SYSTEM_NAME = 'SecTravel：基于门限多密钥多服务器外包计算的车辆动态时空轨迹扭曲验证系统'
export const SERVER_COUNT = 5
export const DEFAULT_THRESHOLD_K = 3
export const STOP_THRESHOLD_MINUTES = 5

export function getQuorum(onlineIds, k = DEFAULT_THRESHOLD_K) {
  const online = [...new Set(onlineIds)].filter(id => Number.isInteger(id) && id >= 1 && id <= SERVER_COUNT).sort((a, b) => a - b)
  return { online, selected: online.slice(0, k), ready: online.length >= k, missing: Math.max(0, k - online.length) }
}

/** Monitor only observations already reached by the animation. k is not a business threshold. */
export function detectTravelAlert(position, reference, stoppedMinutes = 0) {
  const distance = distanceToRouteMeters(position, reference)
  if (distance > SPATIAL_ALERT_THRESHOLD_METERS) {
    return { type: 'spatial', value: distance, limit: SPATIAL_ALERT_THRESHOLD_METERS,
      message: `偏离参考路线 ${distance.toFixed(1)} 米，超过 ${SPATIAL_ALERT_THRESHOLD_METERS} 米。演示车辆与计时已停止。` }
  }
  if (stoppedMinutes > STOP_THRESHOLD_MINUTES) {
    return { type: 'time', value: stoppedMinutes, limit: STOP_THRESHOLD_MINUTES,
      message: `连续停留 ${stoppedMinutes.toFixed(2)} 分钟，超过 ${STOP_THRESHOLD_MINUTES} 分钟。演示车辆与计时已停止。` }
  }
  return null
}
