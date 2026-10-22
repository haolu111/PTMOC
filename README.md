# SecTravel：基于门限多密钥多服务器外包计算的车辆动态时空轨迹扭曲验证系统

> **PTMOC** — Privacy-Preserving Threshold Multi-key Multi-server Outsourced Computation
> 面向车联网 (VANET) 场景，融合 Shamir 秘密共享、陷门加密与同态评估的隐私保护轨迹验证系统。

---

## 当前演示版本

普通行程默认采用 `k=3, n=5` 的密码学门限，不要求用户选择 k；可在折叠的“密码学配置（演示管理）”中调整。页面显示 5 组逻辑服务器/CSP，可切换节点离线、恰好 k 组和不足 k 组；后端会校验参与数量，不足门限时拒绝执行验证与结果恢复。

业务异常阈值与密码学门限分别处理：距离参考路线超过 200 米，或连续停留超过 5 分钟，立即告警并冻结演示车辆位置与计时；关闭提醒不会恢复行驶。验证仅使用截至告警时已采集的轨迹，接口失败不会回退为预设的成功结果。

当前为单机工程原型：逻辑节点在同一后端进程中执行，演示在线状态由界面控制，不能等同于独立多主机部署。当前验证 API 接收轨迹进行诊断，不应把该原型描述成端到端原始轨迹不出终端的生产部署。

### Docker 前后端部署

在项目根目录将 `.env.example` 复制为 `.env`，填入自己的高德 Web 服务 Key，再执行：

```bash
docker compose up -d --build
```

默认访问 `http://127.0.0.1:5173`。只运行前端和后端两个服务，前端代理 `/api` 到后端，后端不映射宿主机端口。需要其他主机访问时，在 `.env` 设置 `PTMOC_BIND_ADDRESS=0.0.0.0`，并按实际网络配置防火墙。真实 `.env` 和密钥不要提交到 GitHub。

前端边界测试：在 `frontend` 目录执行 `node --test tests/demoPolicy.test.mjs`；前端构建执行 `npm run build`。Docker 镜像尚未在本机实际构建验证。

## 📋 目录

- [项目概述](#项目概述)
- [系统架构](#系统架构)
- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [快速开始](#快速开始)
- [前端功能详解](#前端功能详解)
- [后端功能详解](#后端功能详解)
- [核心密码学模块](#核心密码学模块)
- [API 接口文档](#api-接口文档)
- [部署指南](#部署指南)
- [算法流程](#算法流程)
- [路线数据](#路线数据)
- [常见问题](#常见问题)

---

## 项目概述

本系统演示一种**基于门限多密钥多服务器外包计算（PTMOC）的轨迹验证方案**，应用于车辆雾计算场景。原型将密码函数求值与轨迹诊断结合，验证用户行驶轨迹与参考轨迹的一致性，支持以下三种验证场景：

| 场景 | 类别 | 说明 |
|------|------|------|
| 🟢 正常行驶 | `pass` | 用户轨迹与参考轨迹一致，验证通过 |
| 🟡 路线偏移 | `spatial` | 用户中途偏离预设路线，系统检测到空间异常 |
| 🔴 时间异常 | `time` | 用户在特定路段停留时间异常，系统发出警告 |

**核心特点：**
- **密码计算演示**：展示秘密共享、加密份额、部分求值及结果恢复；原型轨迹诊断的可见边界见上文
- **阈值机制**：支持 k/n 门限，至少 k 个参与方协作才能完成解密
- **密文域评估**：通过同态运算在加密状态下检测轨迹偏差
- **实时可视化**：Leaflet 地图动画展示轨迹对比，逐步显示加解密流程

---

## 系统架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        前端 (Vue 3 + Vite)                       │
│  ┌──────────┐  ┌──────────┐  ┌────────────┐  ┌───────────────┐  │
│  │  登录页   │  │  主页    │  │  行程动画页  │  │  调试页(隐藏) │  │
│  │ login    │  │  main    │  │  travel     │  │  debug        │  │
│  └──────────┘  └──────────┘  └────────────┘  └───────────────┘  │
│                       │                                          │
│           ┌───────────┴───────────┐                              │
│           │  PTMOCViewer.vue      │  核心组件 (1540+ 行)         │
│           │  - 地图渲染 (Leaflet)  │                              │
│           │  - 路径动画 (rAF)     │                              │
│           │  - 加密过程展示       │                              │
│           └───────────┬───────────┘                              │
│                       │                                          │
│  ┌────────────────────┼────────────────────┐                     │
│  │ trajectoryData.js  │ ptmocSimulator.js  │  数据 & 模拟层     │
│  │ 20+ 条上海路线      │ 算法模拟 & 评分     │                     │
│  └────────────────────┴────────────────────┘                     │
└─────────────────────────────────────────────────────────────────┘
                              │ HTTP REST
┌─────────────────────────────┴───────────────────────────────────┐
│                    后端 (Spring Boot 3.2.5)                       │
│  ┌──────────────────┐  ┌──────────────────┐                     │
│  │ PtmocController  │  │   PtmocService   │                     │
│  │ REST API 控制器   │──│   算法编排核心     │                     │
│  └──────────────────┘  └────────┬─────────┘                     │
│                                 │                                │
│  ┌──────────┐  ┌──────────┐  ┌──┴───────────┐                   │
│  │ Encoder  │  │ Verifier │  │  core/        │                   │
│  │ 轨迹编码  │  │ 轨迹验证  │  │ BaseModule    │                   │
│  └──────────┘  └──────────┘  │ CryptoModule  │                   │
│                               │ EvalModule    │                   │
│                               └──────────────┘                   │
└─────────────────────────────────────────────────────────────────┘
```

**四参与方模型：**
- **Sender（发送方）**：车辆用户，拥有轨迹数据
- **Server（服务器）**：雾计算节点，执行密文域评估
- **CSP（云服务商）**：提供部分解密密钥
- **Receiver（接收方）**：验证方，最终解密并判定结果

---

## 技术栈

### 前端
| 技术 | 版本 | 用途 |
|------|------|------|
| Vue 3 | ^3.5.33 | 渐进式前端框架 (Composition API) |
| Vite | ^8.0.10 | 构建工具 & 开发服务器 |
| Leaflet | ^1.9.4 | 开源交互式地图 |
| @vue-leaflet/vue-leaflet | ^0.10.1 | Leaflet 的 Vue 3 封装 |
| TypeScript | ~6.0.2 | 类型支持 (devDependency) |

### 后端
| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.2.5 | Java Web 框架 |
| Java | 21 | 运行环境 |
| Maven | 3.x | 项目管理与构建 |
| Gson | 2.10.1 | JSON 序列化/反序列化 |

### 密码学核心
- **Shamir 秘密共享**：多项式插值实现 (k, n) 门限
- **RSA-2048**：公钥加密基础
- **陷门加密**：基于多项式的陷门函数
- **同态评估**：密文域线性运算

---

## 项目结构

```
PTMOC/
├── frontend/                          # Vue 3 前端项目
│   ├── index.html                     # SPA 入口 HTML
│   ├── package.json                   # 依赖 & 脚本
│   ├── vite.config.js                 # Vite 配置
│   ├── vercel.json                    # Vercel 部署配置
│   └── src/
│       ├── main.js                    # Vue 应用入口
│       ├── App.vue                    # 根组件
│       ├── style.css                  # 全局样式
│       ├── components/
│       │   └── PTMOCViewer.vue        # ★ 核心业务组件 (1540+ 行)
│       └── data/
│           ├── trajectoryData.js      # 上海路线数据 (20+ 条)
│           └── ptmocSimulator.js      # PTMOC 算法前端模拟器
│
├── backend/                           # Spring Boot 后端
│   ├── pom.xml                        # Maven 配置
│   └── src/main/java/com/ptmoc/
│       ├── PtmocApplication.java      # Spring Boot 入口
│       ├── config/CorsConfig.java     # CORS 跨域配置
│       ├── controller/
│       │   └── PtmocController.java   # REST API 控制器
│       ├── service/
│       │   ├── PtmocService.java      # ★ 算法编排服务
│       │   ├── TrajectoryEncoder.java # 轨迹编码器
│       │   └── TrajectoryVerifier.java# 轨迹验证器
│       ├── dto/                       # 数据传输对象
│       │   ├── VerifyRequest.java
│       │   ├── VerifyResponse.java
│       │   └── TrajectoryPointDto.java
│       ├── core/                      # 密码学核心模块
│       │   ├── BaseModule.java        # 系统初始化 & 密钥管理
│       │   ├── CryptoModule.java      # 加密/解密
│       │   └── EvalModule.java        # 同态评估
│       ├── model/                     # 数据模型
│       │   ├── Entity.java            # 参与方枚举
│       │   ├── KeyPair.java           # 密钥对
│       │   ├── Polynomial.java        # 多项式
│       │   └── PublicParameters.java  # 公共参数
│       └── util/                      # 工具类
│           ├── CryptoUtil.java
│           └── MathUtil.java
│
├── src/                               # 原始 Java 密码学库 (可独立编译)
│   └── com/ptmoc/
│       ├── core/                      # 同 backend/core/
│       ├── model/                     # 同 backend/model/
│       ├── util/                      # 同 backend/util/
│       └── test/                      # 单元测试
│           ├── SimpleTest.java
│           └── CryptoModuleTest.java
│
├── docs/                              # 项目文档
│   ├── 需求分析文档.md
│   └── *.png                          # 截图
│
├── setup.bat / setup.ps1              # 环境配置脚本
├── build.bat                          # 编译运行脚本
├── quickview.bat                      # 快速查看项目状态
└── viewer.bat                         # 交互式项目查看器
```

---

## 快速开始

### 环境要求

| 工具 | 最低版本 |
|------|----------|
| Node.js | 18+ |
| npm | 9+ |
| JDK | 17+ (后端) |
| Maven | 3.8+ (后端) |

### 1. 前端启动

```bash
cd PTMOC/frontend
npm install
npm run dev
```

浏览器访问 `http://localhost:5173` 即可看到登录页面。

**测试账号：**
- 管理员：`admin` / `admin123`
- 测试用户：`test` / `test123`
- 也可自行注册（数据存储在 localStorage）

### 2. 后端启动

**方式 A（推荐，当前 Mac/JDK8 可用）：**

```bash
cd PTMOC_code
./run-backend.sh
```

该脚本会编译真实 PTMOC 核心并启动 JDK8 Demo HTTP 服务（`http://localhost:8080`），接口与 Spring Boot 一致：

- `GET /api/ptmoc/health`
- `POST /api/ptmoc/verify`

**方式 B（Spring Boot，需要 JDK 17+）：**

```bash
cd PTMOC/backend
mvn clean package -DskipTests
java -jar target/ptmoc-backend-1.0.0.jar
```

后端启动后监听 `http://localhost:8080`，提供 REST API 接口。

前端开发服务器已配置 `/api` 代理到 `8080`；若后端未启动，右侧面板会提示验证未完成，不会回退为预设验证结果。

### 3. 使用一键脚本 (Windows)

```bash
cd PTMOC
setup.bat       # 自动检测/安装 JDK 并编译
build.bat       # 编译并运行测试
```

---

## 前端功能详解

### 页面流程

```
登录页 ──认证通过──▶ 主页 ──选择终点──▶ 行程动画页
  │                   │                    │
  │             选择起点(人民广场)    地图动画 + 加密展示
  │             选择终点              ┌─────────────┐
  │           系统配置密码学门限 k     │ 右侧面板：    │
  │             查看参考轨迹          │ Setup →      │
  │                                  │ KeyGen →     │
  └── (隐藏)调试页 ◀──────────────── │ Encode →     │
       参考/用户轨迹对比             │ Encrypt →    │
       选择偏离点                    │ Eval →       │
                                    │ Decrypt      │
                                    └─────────────┘
```

### 核心特性

#### 1. 地图可视化
- 使用高德地图瓦片 (`webrd0{s}.is.autonavi.com`)
- 内置 WGS-84 → GCJ-02 坐标转换
- 参考轨迹（蓝色虚线）与实际轨迹（实线）对比
- 自定义小车图标沿路线平滑动画

#### 2. 路径动画引擎
- 基于 `requestAnimationFrame` 的连续动画
- 距离插值 (`getPositionAtDist`) 实现平滑位移
- 逐段绘制蓝色轨迹线 (`getBlueLinePoints`)
- 自动平移地图视角跟随小车

#### 3. 异常检测可视化
- **正常路线**：小车顺利到达终点，触发 "✅ 验证通过"
- **路线偏移**：小车沿参考路线行驶至偏离点 → 切换至备用路线 → 3 段后弹窗告警
- **时间异常**：小车正常行驶 → 停留 5 秒 → 弹窗告警 "⏱ 时间异常！"

#### 4. PTMOC 加密流程面板
逐步展示 6 个阶段，每 600ms 推进一步：

| 步骤 | 名称 | 说明 |
|------|------|------|
| 1 | Setup | 初始化公共参数 (安全参数 λ=256) |
| 2 | KeyGen | 为 Sender/Server/CSP/Receiver 生成密钥 |
| 3 | Encode | 编码轨迹偏差 (经度/纬度/时间差) |
| 4 | Encrypt | PTMOC 多接收方加密，生成密文 c₀, c₁, c₂ |
| 5 | Eval | Server 在密文域执行同态评估 |
| 6 | Decrypt | 联合解密并输出验证结果 |

#### 5. 消息编码字段
展示三类轨迹偏差编码：
- **m₁**：经度偏移量编码
- **m₂**：纬度偏移量编码
- **m₃**：时间差编码

---

## 后端功能详解

### PTMOC 算法编排 (`PtmocService.java`)

```java
// 伪代码流程
void executeVerification(request) {
    // 1. Setup - 初始化安全参数
    PublicParameters pp = BaseModule.setup(256);

    // 2. KeyGen - 四方密钥生成
    KeyPair kpSender   = BaseModule.keyGen(pp, Entity.SENDER);
    KeyPair kpServer   = BaseModule.keyGen(pp, Entity.SERVER);
    KeyPair kpCSP      = BaseModule.keyGen(pp, Entity.CSP);
    KeyPair kpReceiver = BaseModule.keyGen(pp, Entity.RECEIVER);

    // 3. Encode - 轨迹偏差编码
    EncodedTrajectory encoded = TrajectoryEncoder.encodeTrajectoryDeviations(
        userTrajectory, referenceTrajectory
    );

    // 4. Encrypt - PTMOC 加密
    Ciphertext ct = CryptoModule.encrypt(pp, encoded, allPublicKeys);

    // 5. Evaluate - 密文域评估
    EvaluationResult evalResult = EvalModule.evaluate(ct, kpServer);

    // 6. Decrypt - 联合解密
    VerificationResult result = CryptoModule.decrypt(ct, kpCSP, kpReceiver);

    // 7. Verify - 输出结论
    return TrajectoryVerifier.verify(result, threshold);
}
```

### CORS 配置
后端默认允许所有来源的跨域请求，适合开发调试。生产环境建议限制为具体域名。

---

## 核心密码学模块

### BaseModule.java
- **`setup(λ)`**：初始化系统，生成模数 N（RSA-2048）、公共参数
- **`keyGen(pp, entity)`**：为指定参与方生成公私钥对
- **密钥管理**：支持密钥的获取、保存与加载

### CryptoModule.java
- **`encrypt(pp, messages, publicKeys)`**：PTMOC 多接收方加密
  - 输入：k+1 个消息（轨迹偏差编码值）
  - 输出：密文组 (c₀, c₁, ..., cₖ)
- **`decrypt(pp, ciphertext, secretKeys)`**：联合解密
  - 需要至少 k 个参与方的私钥协作

### EvalModule.java
- **`evaluate(ciphertext)`**：密文域同态评估
  - 在不解密的情况下对密文执行线性运算
  - 检测轨迹偏差是否超过阈值

### 多项式 (Polynomial.java)
- 用于 Shamir 秘密共享的拉格朗日插值
- 支持系数在模 N 下的多项式运算

---

## API 接口文档

### 1. 轨迹验证

**POST** `/api/ptmoc/verify`

请求体：
```json
{
  "userTrajectory": [
    { "lat": 31.2304, "lng": 121.4737, "timestamp": 1000 },
    { "lat": 31.2320, "lng": 121.4750, "timestamp": 2000 }
  ],
  "referenceTrajectory": [
    { "lat": 31.2304, "lng": 121.4737, "timestamp": 1000 },
    { "lat": 31.2310, "lng": 121.4740, "timestamp": 2000 }
  ],
  "thresholdK": 2,
  "timeAnomaly": false,
  "anomalyDesc": ""
}
```

响应体：
```json
{
  "verificationStatus": "PASS",
  "score": 95.5,
  "metrics": {
    "spatialCoverage": 0.92,
    "spatialDeviation": 0.03,
    "timeCoverage": 0.98,
    "timeDeviation": 0.01
  },
  "abnormalPoints": [],
  "abnormalSegments": [],
  "reasonCodes": [],
  "algorithmSummary": "轨迹验证通过，未发现异常",
  "steps": [ /* 各步骤详情 */ ],
  "totalDuration": 1234,
  "cryptoResult": { /* 密码学结果 */ }
}
```

### 2. 健康检查

**GET** `/api/ptmoc/health`

响应体：
```json
{
  "status": "UP",
  "service": "PTMOC Backend",
  "version": "1.0.0"
}
```

---

## 部署指南

### Vercel 部署（仅前端）

项目已配置 `frontend/vercel.json`：

```bash
cd PTMOC/frontend
npm install
npx vercel --prod
```

或通过 Vercel CLI 使用现有配置：
```bash
cd PTMOC/frontend
vercel --yes --prod
```

### 传统部署

**前端静态文件：**
```bash
cd PTMOC/frontend
npm run build
# 输出目录: frontend/dist/
# 可将 dist/ 内容部署到任意静态服务器 (Nginx, Apache, CDN)
```

**后端 JAR 部署：**
```bash
cd PTMOC/backend
mvn clean package -DskipTests
# 将 target/ptmoc-backend-1.0.0.jar 部署到服务器
# 运行: java -jar ptmoc-backend-1.0.0.jar
```

---

## 算法流程

```
┌──────────────────────────────────────────────────────────────────┐
│                        PTMOC 轨迹验证流程                         │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ① Setup(λ=256)                                                  │
│     ├── 生成大素数 p, q                                           │
│     ├── 生成随机多项式 f(x)                                       │
│     └── 输出公共参数 PP                                           │
│                                                                  │
│  ② KeyGen(PP)                                                    │
│     ├── Sender:   (pkₛ, skₛ)                                     │
│     ├── Server:   (pkₑ, skₑ)                                     │
│     ├── CSP:      (pk꜀, sk꜀)                                     │
│     └── Receiver: (pkᵣ, skᵣ)                                     │
│                                                                  │
│  ③ Encode(轨迹₁, 轨迹₂)                                          │
│     ├── 计算逐点经纬度偏移 Δlat, Δlng                              │
│     ├── 计算时间差 Δt                                             │
│     └── 编码为 m₁, m₂, m₃                                        │
│                                                                  │
│  ④ Encrypt(PP, m₁..m₃, pkₐₗₗ)                                   │
│     ├── 陷门加密消息 m                                            │
│     ├── 计算哈希链 H₁, H₂, H₃                                     │
│     └── 输出密文 CT = (c₀, c₁, c₂)                                │
│                                                                  │
│  ⑤ Evaluate(CT)                                                  │
│     ├── Server 使用 skₑ 计算                                    │
│     ├── 密文域同态评估                                            │
│     └── 输出中间评估值 ev                                         │
│                                                                  │
│  ⑥ Decrypt(CT, ev, sk꜀, skᵣ)                                    │
│     ├── CSP 提供部分解密                                          │
│     ├── Receiver 完成最终解密                                     │
│     └── 输出明文验证结果                                          │
│                                                                  │
│  ⑦ Verify(结果, threshold)                                       │
│     ├── 空间覆盖率 ≥ 阈值?                                        │
│     ├── 空间偏移 ≤ 阈值?                                          │
│     ├── 时间覆盖率 ≥ 阈值?                                        │
│     ├── 时间偏移 ≤ 阈值?                                          │
│     └── 综合判定 → PASS / PARTIAL / FAIL                          │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

---

## 路线数据

项目预置了 **20 条上海市区路线**，当前启用 3 条（均从人民广场出发）：

| # | 路线 | 类别 | 距离 | 说明 |
|---|------|------|------|------|
| 1 | 人民广场 → 南京路 | `pass` | ~3km | 正常路线，验证通过 |
| 2 | 人民广场 → 世纪公园 | `spatial` | ~8km | 路线偏移，中途偏离 |
| 3 | 人民广场 → 浦东机场 | `time` | ~45km | 时间异常，某段停留过久 |

**已预置但未启用的路线（可在 `trajectoryData.js` 中开启）：**

外滩→陆家嘴、静安寺→中山公园、南京路→豫园、徐家汇→上海南站、外滩→五角场、南京路→陆家嘴、上海火车站→虹桥火车站、静安寺→徐家汇、五角场→世纪公园、陆家嘴→上海火车站、龙阳路→张江高科、上海南站→外滩、中山公园→世纪公园、徐家汇→迪士尼、虹桥火车站→外滩、五角场→陆家嘴、豫园→龙阳路

每条主路线都配有对应的 `_alt` 备用路线，用于空间偏移场景的轨迹切换。

---

## 常见问题

### Q: 前端能否独立运行？
**可以。** 前端内置了 `ptmocSimulator.js` 模拟器和预置的 `CRYPTO_DATA`，无需后端即可演示完整的加密验证流程。

### Q: 地图为什么不显示？
确保网络可访问高德地图瓦片服务。地图使用 GCJ-02 坐标系，代码中已实现 WGS-84 转换。

### Q: 如何添加新路线？
在 `frontend/src/data/trajectoryData.js` 中：
1. 在 `ROUTE_COORDS` 中添加新路线的坐标数组（可添加 `_alt` 备用路线）
2. 在 `ROUTE_COMBOS` 中注册路线（指定起点、终点、类别）
3. 重新运行 `npm run dev`

### Q: 普通用户需要选择门限 k 吗？
不需要。系统默认 `k=3, n=5`；演示管理可在“密码学配置”中选择 k=2、3、4、5。k 表示恢复结果所需的协作节点组数，与 200 米或 5 分钟的业务异常阈值不同。

### Q: 后端如何连接前端？
前端请求 `/api/ptmoc/verify`（Vite 代理到 `localhost:8080`）。先运行 `./run-backend.sh` 或 Spring Boot 后端；后端不可用时显示验证未完成，不输出预设结论。

### Q: 调试页面怎么访问？
调试页面入口已在登录页隐藏。开发者可在 `PTMOCViewer.vue` 中取消注释调试入口链接，或在浏览器控制台执行 `currentPage='debug'`。

---

## 许可

本项目仅用于学术研究与教学演示目的。

---

*最后更新：2026 年 10 月 22 日*
