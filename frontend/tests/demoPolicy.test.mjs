import test from 'node:test'
import assert from 'node:assert/strict'
import { getQuorum, detectTravelAlert } from '../src/utils/demoPolicy.js'
import { buildTraveledTrajectory } from '../src/utils/routeDeviation.js'

test('3/5 permits any three distinct logical nodes, tolerates two offline nodes', () => {
  assert.equal(getQuorum([1, 2, 3, 4, 5]).ready, true)
  assert.deepEqual(getQuorum([2, 4, 5]).selected, [2, 4, 5])
  assert.equal(getQuorum([2, 4, 5]).ready, true)
  assert.equal(getQuorum([1, 2]).ready, false)
  assert.equal(getQuorum([1, 1, 2]).ready, false)
  assert.equal(getQuorum([0, 6, 1]).ready, false)
})

test('stop threshold is strictly more than five minutes, independent of k', () => {
  const reference = [{ lat: 31, lng: 121 }, { lat: 31, lng: 121.01 }]
  assert.equal(detectTravelAlert(reference[0], reference, 5), null)
  assert.equal(detectTravelAlert(reference[0], reference, 5.001).type, 'time')
})

test('spatial alert uses distance to route, not same-index point separation', () => {
  const reference = [{ lat: 0, lng: 0 }, { lat: 0, lng: 0.1 }]
  const atMeters = meters => ({ lat: meters / (6371000 * Math.PI / 180), lng: 0.05 })
  assert.equal(detectTravelAlert({ lat: 0, lng: 0.08 }, reference), null)
  assert.equal(detectTravelAlert(atMeters(199.99), reference), null)
  assert.equal(detectTravelAlert(atMeters(200.01), reference).type, 'spatial')
})

test('verification snapshot includes only observed points and interpolated trigger point', () => {
  const points = [0, 1, 2, 3].map(i => ({ lat: i, lng: i, timestamp: i * 1000 }))
  const result = buildTraveledTrajectory({ userTrajectory: points, referenceTrajectory: points }, 1, .5)
  assert.equal(result.userTrajectory.length, 3)
  assert.equal(result.userTrajectory.at(-1).timestamp, 1500)
  assert.equal(result.referenceTrajectory.at(-1).lat, 1.5)
  assert.equal(points.length, 4)
})
