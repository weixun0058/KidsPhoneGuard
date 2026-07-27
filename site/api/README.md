# 拉钩守护反馈收集后端

## 环境要求
- Python 3.8+
- pip

## 安装依赖

```bash
cd api
pip install -r requirements.txt
```

## 启动服务

```bash
uvicorn main:app --host 0.0.0.0 --port 8000
```

## 接口说明

### 提交反馈
```bash
POST /api/feedback
Content-Type: application/json

{
  "deviceModel": "Redmi K70",
  "systemVersion": "Android 14",
  "issueType": "拦截未生效",
  ...
}
```

### 列出所有反馈
```bash
GET /api/feedback/list
```

### 查看单个反馈
```bash
GET /api/feedback/feedback_2026-04-28-15-30-25_a1b2c3.json
```

## 反馈存储位置

所有反馈保存在 `../feedback-data/` 目录下，每个反馈一个 JSON 文件：
```
feedback-data/
├── feedback_2026-04-28-15-30-25_a1b2c3.json
├── feedback_2026-04-28-16-12-08_d4e5f6.json
└── ...
```

## 前端配置

在 `site-config.json` 中设置：
```json
{
  "feedbackEndpoint": "http://你的服务器IP:8000/api/feedback"
}
```
