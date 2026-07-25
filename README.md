# 智能健身房管理系统 (Smart Gym)

> 基于 ReAct Agent + RAG 的 AI 全栈健身房管理平台，覆盖 PC 管理后台与微信小程序端。

`Spring Boot` `Vue 3` `LangChain4j` `DeepSeek` `MyBatis-Plus` `MySQL` `MongoDB` `Redis` `微信原生小程序` `Python Flask` `face_recognition`

---

## 目录

- [核心功能亮点](#核心功能亮点)
- [AI 智能助手实现细节](#ai-智能助手实现细节)
- [架构演进与当前状态](#架构演进与当前状态)
- [技术栈一览](#技术栈一览)
- [快速启动](#快速启动)
- [项目结构](#项目结构)

---

## 核心功能亮点

### 1. AI 智能健身助手

基于 **ReAct Agent 模式 + 工具调用 + RAG 知识库 + 实时用户上下文** 的领域智能助手，不是通用聊天机器人。

- **意图识别与工具调用**：自动判断用户意图，调用对应的业务工具（查询可预约团课、代用户预约课程、查体测历史、找教练信息），完成操作后返回结果
- **实时上下文注入**：每次对话动态拼接会员的等级、身高体重、剩余课时、今日待上课、课程包余量、会员到期日等数据，让 AI 的回答高度个性化
- **RAG 知识库**：本地健身领域知识文档 → AllMiniLmL6V2 向量化 → 语义检索，回答专业健身问题
- **流式输出**：SSE 逐字推送，可附带健身动作示意图片
- **对话记忆**：MongoDB 持久化，按 memberId + sessionId 隔离

### 2. 刷脸签到

- 独立 Python Flask 微服务，基于 face_recognition 库实现人脸注册与识别
- 前端摄像头采集 → base64 传输 → 特征提取与比对
- 配合 GPS 定位围栏校验，防止异地签到

### 3. 体测评估引擎

- 自研 AssessmentScoringEngine 评分引擎，支持多维度体测评分
- AI 自动生成体测评估建议（AssessmentAiSuggestionParser）
- 生成结构化评估报告

### 4. 团课/私教全流程管理

- 团课排课、预约、名额管理与自动扣减
- 私教课程包（次卡/期限卡）管理
- 会员等级映射免费私教次数，按月重置
- 自动化的预约冲突检测

### 5. 管理后台看板

- ECharts 数据可视化（会员增长、课程预约趋势等）
- 会员/教练/积分/竞赛/饮食记录/系统配置全功能管理
- 多角色权限体系（管理员 / 会员 / 教练）

### 6. 微信小程序端

- 刷脸签到（手机端便捷打卡）
- 教练工作台（查看预约学员、请假申请、学员管理）
- 饮食记录（移动端随手记录）
- 竞赛报名与详情查看
- 体测评估报告查看、积分兑换

---

## AI 智能助手实现细节

### 模型接入

- **LLM**：DeepSeek API（deepseek-chat），通过 LangChain4j 的 OpenAI 兼容接口接入
- **参数**：temperature 0.7，max-tokens 4096，60s 超时

### Agent 架构（ReAct 模式）

```
用户输入 → analyze() 分析意图 → decideAction() 决策动作
        → act() 执行工具（调用 LLM 或业务接口）
        → observe() 观察结果 → 判断是否完成
        → 未完成则循环，已完成则返回最终回复
```

GymAssistantAgent 继承 ToolCallAgent → ReActAgent → BaseAgent，每步记录 state，支持多步推理。

### 工具注册（@Tool）

通过 LangChain4j 的 @Tool 注解暴露给 LLM：

| 工具 | 功能 |
|------|------|
| queryAvailableClasses | 按时间范围查询可预约团课 |
| bookGroupClass | 为会员预约指定团课 |
| queryFitnessTest | 查询会员体测历史 |
| queryTrainerInfo | 查询教练信息 |

### RAG 知识库

- **文档**：fitness_knowledge.txt（本地健身知识文本）
- **分块**：每 200 字符一块，重叠 20 字符
- **向量化**：AllMiniLmL6V2EmbeddingModel（本地运行，无需外部 API）
- **存储与检索**：InMemoryEmbeddingStore，返回 top-2 相关片段

### 上下文与提示词设计

每次对话构建 buildUserContext()，内容包含：

```
- 会员ID / 姓名 / 等级
- 会员状态（有效/即将到期/已过期）
- 今日待上课数量
- 剩余课程包课时数
- 本月免费私教剩余次数
- 身高 / 体重
```

上下文 + 知识库检索结果 + 对话历史 → 一起送入 LLM，确保回复贴合会员实际情况。

### 记忆存储

- 基于 LangChain4j 的 ChatMemoryStore 接口实现
- 后端存储：MongoDB，文档结构为 ChatHistoryDoc { id, messages[] }
- 隔离策略：memberId_sessionId 组合键，不同会员/会话互不干扰

---

## 架构演进与当前状态

### V1.0（已完成）
纯网页端健身应用，实现核心训练记录功能。

### V2.0（重构中，当前状态）
引入小程序端，从单应用向多端架构演进。

- **已完成**：API 层重设计，以支持多端复用；小程序端主框架搭建完毕
- **进行中/待开发**：历史数据迁移模块、部分交互动效优化、单元测试覆盖率提升

### 技术选型说明

小程序采用 **微信原生框架**，原因如下：

- 与微信生态深度集成（登录、支付、订阅消息等），开箱即用
- 相比 uni-app / Taro 等跨端方案，原生框架性能更好、调试更直接
- 当前阶段主要覆盖微信用户，暂无多小程序平台需求，原生方案开发成本最低

---

## 技术栈一览

| 层级 | 技术 | 说明 |
|------|------|------|
| 后端 | Spring Boot 2.7 + MyBatis-Plus | RESTful API，多端共用 |
| 数据库 | MySQL + MongoDB + Redis | MySQL 存业务数据，MongoDB 存对话记忆，Redis 缓存 |
| PC 前端 | Vue 3 + Vite + Element Plus + ECharts + Axios | 管理后台 + 会员面板 |
| 微信小程序 | 微信原生小程序 | 移动端高频轻量操作 |
| AI | DeepSeek API + LangChain4j + ReAct Agent + RAG | AI 智能助手 |
| 人脸服务 | Python Flask + face_recognition | 独立微服务，刷脸签到 |

---

## 快速启动

### 环境要求

- JDK 17
- Maven 3.8+
- Node.js 18+
- MySQL 8.0
- MongoDB
- Redis
- Python 3.9+（仅人脸服务需要）

### 后端启动

```bash
cd backend
# 配置 application.yml 中的数据库连接与 API Key
mvn clean install -DskipTests
mvn spring-boot:run
```

### PC 前端启动

```bash
cd frontend
npm install
npm run dev
```

### 人脸服务启动

```bash
cd backend/face_service
pip install -r requirements.txt
python app.py
```

### 微信小程序

用微信开发者工具打开 wx-mp-gym 目录，填入 AppID 即可。

### 环境变量

| 变量 | 说明 |
|------|------|
| MYSQL_PASSWORD | MySQL 密码 |
| DEEPSEEK_API_KEY | DeepSeek API 密钥 |
| WX_APPID | 微信小程序 AppID |
| WX_SECRET | 微信小程序 Secret |

---

## 项目结构

```
1-my-original-project/
├── backend/                    # Spring Boot 后端
│   ├── src/main/java/com/gym/
│   │   ├── ai/                 # AI 模块（核心亮点）
│   │   │   ├── agent/          # ReAct Agent 实现
│   │   │   ├── config/         # LangChain4j 配置
│   │   │   ├── memory/         # MongoDB 对话记忆
│   │   │   ├── rag/            # 知识库检索
│   │   │   └── tool/           # @Tool 工具注册
│   │   ├── assessment/         # 体测评估引擎
│   │   ├── controller/         # REST 控制器
│   │   ├── mapper/             # MyBatis-Plus 映射
│   │   └── service/            # 业务逻辑层
│   └── face_service/           # Python Flask 人脸识别服务
├── frontend/                   # Vue 3 PC 前端
│   └── src/views/
│       ├── admin/              # 管理后台页面
│       └── member/             # 会员面板页面（含 AIChat.vue）
└── wx-mp-gym/                  # 微信小程序
    └── pages/                  # 页面（ai-chat, face-checkin, courses 等）
```
