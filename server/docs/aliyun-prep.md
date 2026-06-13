# 阿里云控制台准备清单

在部署 `server/` 到 ECS 之前，请按顺序完成以下控制台操作。

## 1. OSS Bucket（必须与 ECS 同地域：华东1 杭州）

1. 登录 [OSS 控制台](https://oss.console.aliyun.com/)
2. 创建 Bucket（若尚无杭州桶）：
   - 地域：`华东1（杭州）` / `cn-hangzhou`
   - 存储类型：标准存储
   - 读写权限：**私有**
   - 版本控制：关闭
3. 记录 Bucket 名称，填入服务器 `.env` 的 `OSS_BUCKET`
4. 生命周期规则（保护 40GB 资源包）：
   - 前缀：`audio/`
   - 操作：删除对象
   - 天数：30

## 2. RAM 角色（ECS 免密钥访问 OSS）

### 2.1 创建自定义策略

RAM → 权限管理 → 策略 → 创建策略（JSON）：

```json
{
  "Version": "1",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "oss:PutObject",
        "oss:GetObject",
        "oss:DeleteObject",
        "oss:ListObjects",
        "oss:ListObjectsV2"
      ],
      "Resource": [
        "acs:oss:*:*:YOUR_BUCKET_NAME",
        "acs:oss:*:*:YOUR_BUCKET_NAME/*"
      ]
    }
  ]
}
```

将 `YOUR_BUCKET_NAME` 替换为实际桶名。

### 2.2 创建角色并绑定 ECS

1. RAM → 角色 → 创建角色
   - 信任主体：阿里云服务 → ECS
   - 角色名称：`TimedRecorderECSRole`
2. 为角色授权上一步自定义策略
3. ECS 控制台 → 实例 `i-bp14rttl2bfjj9akkbes` → 更多 → 实例状态 → 绑定/更换 RAM 角色 → 选择 `TimedRecorderECSRole`

## 3. 百炼 API Key

1. 开通 [百炼 / 灵积](https://help.aliyun.com/zh/model-studio/get-api-key)
2. 创建 API Key，记录为 `DASHSCOPE_API_KEY`
3. 确保已开通：`fun-asr`（录音识别）、`qwen-plus`（文本摘要）

## 4. 安全组

ECS → 安全组 → 配置规则（入方向）：

| 端口 | 协议 | 授权对象 | 说明 |
|------|------|----------|------|
| 22 | TCP | 你的办公 IP/32 | SSH，勿长期 0.0.0.0/0 |
| 443 | TCP | 0.0.0.0/0 | HTTPS API |
| 80 | TCP | 0.0.0.0/0 | 可选，HTTP 跳转 |

**不要开放** 8000、6379。

## 5. 填写 `.env`

复制 `server/.env.example` 为 `server/.env` 并填写：

```env
OSS_REGION=cn-hangzhou
OSS_BUCKET=你的桶名
OSS_RAM_ROLE_NAME=TimedRecorderECSRole
DASHSCOPE_API_KEY=sk-xxx
API_KEY=与App设置中一致的密钥
```

## 6. 验证 RAM 角色（部署后）

SSH 登录 ECS 执行：

```bash
curl -s http://100.100.100.200/latest/meta-data/ram/security-credentials/TimedRecorderECSRole
```

应返回含 `AccessKeyId` 的 JSON。
