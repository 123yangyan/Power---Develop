# 定时录音助手 — 后端服务

对接 Android 客户端 [`AudioApiService`](../app-main/core/network/src/main/kotlin/com/timedrecorder/core/network/AudioApiService.kt)。

## 架构

- **Nginx**：443 HTTPS 终止
- **FastAPI**：上传 / 结果查询 API
- **Worker**：OSS → Fun-ASR → Qwen 分析
- **Redis**：异步任务队列
- **SQLite**：元数据与处理结果
- **OSS**：音频文件存储（ECS RAM 角色 + 内网 Endpoint）

## 快速部署（ECS）

```bash
# 1. 控制台准备（见 docs/aliyun-prep.md）
# 2. 清空旧环境
sudo bash scripts/wipe-server.sh

# 3. 安装 Docker
sudo bash scripts/install-docker.sh

# 4. 配置环境变量
cp .env.example .env
vim .env

# 5. 部署
sudo bash scripts/deploy.sh

# 6. 验证
bash scripts/verify.sh https://120.26.204.190 your-api-key /path/to/test.m4a
```

## App 配置

- Base URL：`https://120.26.204.190`
- API Key：与 `.env` 中 `API_KEY` 一致
- Debug 构建已允许自签 HTTPS（见 `core/network` 模块）

## API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/health` | 健康检查 |
| POST | `/api/audio/upload` | 上传音频切片 |
| GET | `/api/audio/result?fileId=` | 查询单条结果 |
| POST | `/api/audio/result/batch` | 批量查询结果 |
