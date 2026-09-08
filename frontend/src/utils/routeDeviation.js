export const SPATIAL_ALERT_THRESHOLD_METERS = 200

/** 截取已行驶部分，并在当前线段内插值，避免验证尚未经过的轨迹点。 */
export function buildTraveledTrajectory(trajectory, segIdx, fraction) {
  const takeTraveledPoints = (points) => {
    const traveled = points.slice(0, segIdx + 1).map(point => ({ ...point }))
    if (fraction > 0 && segIdx + 1 < points.length) {
      const a = points[segIdx]
      const b = points[segIdx + 1]
      traveled.push({
        lat: a.lat + (b.lat - a.lat) * fraction,
        lng: a.lng + (b.lng - a.lng) * fraction,
        timestamp: Math.round(a.timestamp + (b.timestamp - a.timestamp) * fraction)
      })
    }
    return traveled
  }
  return {
    ...trajectory,
    userTrajectory: takeTraveledPoints(trajectory.userTrajectory),
    referenceTrajectory: takeTraveledPoints(trajectory.referenceTrajectory)
  }
}

/** 同一坐标系下，当前位置到参考折线的最短距离；局部平面投影，单位为米。 */
export function distanceToRouteMeters(position, referencePoints) {
  if (!referencePoints.length) return Infinity

  const metersPerRadian = 6371000
  const toRad = Math.PI / 180
  const lngScale = metersPerRadian * toRad * Math.cos(position.lat * toRad)
  const latScale = metersPerRadian * toRad
  const project = (point) => ({
    x: (point.lng - position.lng) * lngScale,
    y: (point.lat - position.lat) * latScale
  })

  let a = project(referencePoints[0])
  let minDistance = Math.hypot(a.x, a.y)
  for (let i = 1; i < referencePoints.length; i++) {
    const b = project(referencePoints[i])
    const dx = b.x - a.x
    const dy = b.y - a.y
    const lengthSquared = dx * dx + dy * dy
    const t = lengthSquared > 0
      ? Math.max(0, Math.min(1, -(a.x * dx + a.y * dy) / lengthSquared))
      : 0
    minDistance = Math.min(minDistance, Math.hypot(a.x + t * dx, a.y + t * dy))
    a = b
  }
  return minDistance
}
