/**
 * PTMOC 加密算法模拟器
 * 模拟后端加密运算过程，用于前端展示
 */

// PTMOC算法步骤定义
export const ALGORITHM_STEPS = [
  {
    id: 'init',
    name: '系统初始化',
    description: 'PTMOC.Setup: 生成公共参数 (λ, p₀, η, 陷门置换对)',
    duration: 800,
    detail: '生成安全参数λ=256，大素数p₀，陷门置换对(RSA-2048)，多项式倍数η=512'
  },
  {
    id: 'keygen',
    name: '密钥生成',
    description: 'PTMOC.KeyGen: 为Sender/Server/CSP/Receiver生成密钥对',
    duration: 600,
    detail: '为4个实体分别生成RSA非对称密钥对和AES对称密钥'
  },
  {
    id: 'encode',
    name: '消息编码',
    description: '将轨迹坐标和时间戳编码为多项式消息 m₁, m₂, ..., mₖ',
    duration: 500,
    detail: '将经纬度偏移量和时间差编码为BigInteger消息，准备加密输入'
  },
  {
    id: 'encrypt',
    name: 'PTMOC加密',
    description: 'PTMOC.Enc: 秘密共享 + 陷门加密 + 对称加密辅助份额',
    duration: 1500,
    detail: '对每个消息执行：(1)Shamir秘密共享 (2)CRT加密份额 (3)陷门加密随机数r (4)对称加密交叉份额'
  },
  {
    id: 'evaluate',
    name: '密文域评估',
    description: 'PTMOC.Eval: Server与CSP协同完成密文域多项式计算',
    duration: 2000,
    detail: 'Server解密陷门获取r/p/q，重构加密秘密，计算多项式密文结果，CSP添加随机掩码r\''
  },
  {
    id: 'decrypt',
    name: '结果解密',
    description: 'PTMOC.Dec: Receiver恢复加密域评估结果',
    duration: 800,
    detail: 'Receiver利用私钥解密r和r\'，计算最终明文结果 f(m₁,...,mₖ) mod p₀'
  },
  {
    id: 'verify',
    name: '轨迹验证',
    description: '将解密结果与参考轨迹对比，计算相似度和异常段',
    duration: 1000,
    detail: '计算DTW距离、偏移距离、命中率，标记异常点段，生成验证结论'
  }
]

/**
 * 模拟PTMOC算法运行
 * @param {Function} onStepUpdate - 步骤更新回调
 * @param {Object} userTrajectory - 用户轨迹
 * @param {Object} referenceTrajectory - 参考轨迹
 * @param {number} k - 阈值参数
 * @returns {Promise<Object>} 验证结果
 */
export function simulatePTMOC(userTrajectory, referenceTrajectory, k = 3, onStepUpdate, timeAnomaly = false, anomalyDesc = null) {
  return new Promise((resolve) => {
    const results = {
      steps: [],
      algorithmSummary: {},
      verificationResult: {},
      totalDuration: 0
    }

    let totalStart = performance.now()
    let stepIndex = 0

    function runNextStep() {
      if (stepIndex >= ALGORITHM_STEPS.length) {
        results.totalDuration = parseFloat((performance.now() - totalStart).toFixed(0))
        // 生成最终结果
        results.verificationResult = generateVerificationResult(userTrajectory, referenceTrajectory, k, timeAnomaly, anomalyDesc)
        results.algorithmSummary = {
          securityParameter: 256,
          thresholdK: k,
          polynomialDegree: 2,
          rsaKeySize: 2048,
          totalSteps: ALGORITHM_STEPS.length,
          encryptionType: 'PTMOC (Privacy-Preserving Threshold Multi-Owner Cryptography)',
          dataPoints: userTrajectory.length
        }
        resolve(results)
        return
      }

      const step = ALGORITHM_STEPS[stepIndex]
      const stepStart = performance.now()

      if (onStepUpdate) {
        onStepUpdate({
          currentStep: stepIndex,
          step: step,
          status: 'running',
          progress: ((stepIndex) / ALGORITHM_STEPS.length) * 100
        })
      }

      setTimeout(() => {
        const stepDuration = performance.now() - stepStart
        results.steps.push({
          ...step,
          actualDuration: parseFloat(stepDuration.toFixed(0)),
          status: 'completed'
        })

        if (onStepUpdate) {
          onStepUpdate({
            currentStep: stepIndex,
            step: step,
            status: 'completed',
            progress: ((stepIndex + 1) / ALGORITHM_STEPS.length) * 100
          })
        }

        stepIndex++
        runNextStep()
      }, step.duration)
    }

    runNextStep()
  })
}

/**
 * 生成模拟验证结果
 * 综合空间偏移和时间偏差进行验证
 */
function generateVerificationResult(userTrajectory, referenceTrajectory, k, timeAnomaly, anomalyDesc) {
  const numPoints = Math.min(userTrajectory.length, referenceTrajectory.length)

  // 1. 计算空间偏移
  const deviations = []
  const spatialAbnormalPoints = []
  const abnormalSegments = []
  const matchedSegments = []

  let inAbnormalSegment = false
  let segmentStart = -1

  for (let i = 0; i < numPoints; i++) {
    const latDiff = userTrajectory[i].lat - referenceTrajectory[i].lat
    const lngDiff = userTrajectory[i].lng - referenceTrajectory[i].lng
    const distance = Math.sqrt(latDiff * latDiff + lngDiff * lngDiff) * 111000
    deviations.push(parseFloat(distance.toFixed(2)))

    const isAbnormal = distance > 50
    if (isAbnormal) {
      spatialAbnormalPoints.push({
        index: i,
        lat: userTrajectory[i].lat,
        lng: userTrajectory[i].lng,
        deviation: parseFloat(distance.toFixed(2)),
        timestamp: userTrajectory[i].timestamp,
        type: 'spatial'
      })
      if (!inAbnormalSegment) {
        segmentStart = i
        inAbnormalSegment = true
      }
    } else {
      if (inAbnormalSegment) {
        abnormalSegments.push({ start: segmentStart, end: i - 1, type: 'spatial' })
        inAbnormalSegment = false
      }
      matchedSegments.push({ start: i, end: i })
    }
  }
  if (inAbnormalSegment) {
    abnormalSegments.push({ start: segmentStart, end: numPoints - 1, type: 'spatial' })
  }

  // 2. 计算时间偏差
  const timeDeviations = []
  const timeAbnormalPoints = []
  const timeAbnormalSegments = []
  let inTimeAbnormalSegment = false
  let timeSegStart = -1

  for (let i = 0; i < numPoints; i++) {
    const timeDiff = Math.abs(userTrajectory[i].timestamp - referenceTrajectory[i].timestamp)
    const timeDiffMin = timeDiff / 60000 // 转换为分钟
    timeDeviations.push(parseFloat(timeDiffMin.toFixed(2)))

    const isTimeAbnormal = timeDiffMin > 5 // 5分钟阈值
    if (isTimeAbnormal) {
      timeAbnormalPoints.push({
        index: i,
        lat: userTrajectory[i].lat,
        lng: userTrajectory[i].lng,
        timeDeviation: parseFloat(timeDiffMin.toFixed(2)),
        userTime: userTrajectory[i].time,
        refTime: referenceTrajectory[i].time,
        type: 'time'
      })
      if (!inTimeAbnormalSegment) {
        timeSegStart = i
        inTimeAbnormalSegment = true
      }
    } else {
      if (inTimeAbnormalSegment) {
        timeAbnormalSegments.push({ start: timeSegStart, end: i - 1, type: 'time' })
        inTimeAbnormalSegment = false
      }
    }
  }
  if (inTimeAbnormalSegment) {
    timeAbnormalSegments.push({ start: timeSegStart, end: numPoints - 1, type: 'time' })
  }

  // 合并空间异常点与时间异常点
  const abnormalPoints = [...spatialAbnormalPoints, ...timeAbnormalPoints]
  const allAbnormalSegments = [...abnormalSegments, ...timeAbnormalSegments]

  // 合并相邻matched segments
  const mergedMatched = []
  let current = null
  for (const seg of matchedSegments) {
    if (current && seg.start === current.end + 1) {
      current.end = seg.end
    } else {
      if (current) mergedMatched.push(current)
      current = { ...seg }
    }
  }
  if (current) mergedMatched.push(current)

  // 3. 综合评分
  const avgDeviation = deviations.reduce((a, b) => a + b, 0) / deviations.length
  const maxDeviation = Math.max(...deviations)
  const spatialAbnormalRatio = spatialAbnormalPoints.length / numPoints
  const spatialCoverageRatio = 1 - spatialAbnormalRatio

  const avgTimeDeviation = timeDeviations.reduce((a, b) => a + b, 0) / timeDeviations.length
  const maxTimeDeviation = Math.max(...timeDeviations)
  const timeAbnormalRatio = timeAbnormalPoints.length / numPoints
  const timeCoverageRatio = 1 - timeAbnormalRatio

  // 评分：空间覆盖率40% + 空间偏移20% + 时间覆盖率25% + 时间偏移15%
  const spatialScore = spatialCoverageRatio * 40 + Math.max(0, 1 - avgDeviation / 200) * 20
  const timeScore = timeCoverageRatio * 25 + Math.max(0, 1 - avgTimeDeviation / 30) * 15
  const score = parseFloat((spatialScore + timeScore).toFixed(1))

  let verificationStatus
  if (score >= 80) verificationStatus = '通过'
  else if (score >= 60) verificationStatus = '部分通过'
  else verificationStatus = '不通过'

  // 生成失败原因
  const reasonCodes = []
  const reasonDetails = []
  if (spatialAbnormalRatio > 0.3) {
    reasonCodes.push('DEVIATION_TOO_LARGE')
    reasonDetails.push('空间/时间偏移过大')
  }
  if (timeAbnormalRatio > 0.2) {
    reasonCodes.push('TIME_DEVIATION')
    reasonDetails.push('空间/时间偏移过大：' + (anomalyDesc || `平均时间偏差${avgTimeDeviation.toFixed(1)}分钟`))
  }
  if (spatialAbnormalRatio > 0.1 && spatialAbnormalRatio <= 0.3) {
    reasonCodes.push('PARTIAL_DEVIATION')
  }
  if (reasonCodes.length === 0 && score < 80) {
    reasonCodes.push('PARTIAL_DEVIATION')
  }

  return {
    verificationStatus,
    score,
    matchedSegments: mergedMatched,
    abnormalSegments: allAbnormalSegments,
    abnormalPoints,
    timeAbnormalSegments,
    timeAbnormalPoints,
    reasonCodes,
    reasonDetails,
    anomalyDesc: anomalyDesc || null,
    metrics: {
      avgDeviation: parseFloat(avgDeviation.toFixed(2)),
      maxDeviation: parseFloat(maxDeviation.toFixed(2)),
      coverageRatio: parseFloat((spatialCoverageRatio * 100).toFixed(1)),
      abnormalRatio: parseFloat((spatialAbnormalRatio * 100).toFixed(1)),
      avgTimeDeviation: parseFloat(avgTimeDeviation.toFixed(2)),
      maxTimeDeviation: parseFloat(maxTimeDeviation.toFixed(2)),
      timeCoverageRatio: parseFloat((timeCoverageRatio * 100).toFixed(1)),
      totalPoints: numPoints,
      spatialAbnormalCount: spatialAbnormalPoints.length,
      timeAbnormalCount: timeAbnormalPoints.length
    }
  }
}
