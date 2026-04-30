# KidsPhoneGuard 测试站

这个目录包含一个适合“小范围亲友测试”的静态网站，用于集中承载以下内容：

- 程序说明
- 测试版 APK 分发
- 结构化问题反馈
- 常见问题说明

## 目录说明

- `index.html`：页面结构
- `styles.css`：页面样式
- `script.js`：配置加载、反馈提交、本地导出逻辑
- `site-config.json`：可修改的站点配置

## 如何替换下载链接

编辑 `site-config.json`：

```json
{
  "downloadUrl": "https://your-domain.example.com/KidsPhoneGuard-test.apk"
}
```

如果 `downloadUrl` 为空，页面会自动禁用下载按钮。

## 如何接入真实反馈接口

编辑 `site-config.json`：

```json
{
  "feedbackEndpoint": "https://your-domain.example.com/api/test-feedback"
}
```

前端会向该地址发送 `POST` 请求，请求体为 JSON。

如果 `feedbackEndpoint` 为空，页面会启用无后端降级模式：

- 表单数据先保存到浏览器 `localStorage`
- 用户可以点击“导出本地反馈”下载 JSON 文件

## 本地预览

在 PowerShell 中进入仓库根目录后运行：

```powershell
python -m http.server 8080
```

然后打开：

```text
http://localhost:8080/site/test-portal/
```

## 后续建议

- 把 APK 下载地址换成真实可访问链接
- 把联系人和反馈渠道改成你的真实信息
- 如果要正式对外可访问，建议再补一页隐私政策和测试说明页
