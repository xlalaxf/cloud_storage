# 部署文档

本文档说明 Cloud Storage 的部署方式和生产环境注意事项。

## 环境要求

- JDK 21+
- Maven Wrapper，项目已内置 `backend/mvnw.cmd`
- Node.js 20+
- MySQL 8+
- Nginx，可选但推荐
- 足够容量的磁盘空间，用于正式文件和上传临时分片

## 配置文件

后端配置文件位于：

```text
backend/src/main/resources/application.properties
```

示例配置位于：

```text
backend/src/main/resources/application-example.properties
```

生产环境不要提交真实 `application.properties` 到 GitHub。建议通过服务器环境变量、外部配置文件或 CI/CD Secret 注入敏感配置。

关键配置：

```properties
server.port=8080

spring.datasource.url=jdbc:mysql://127.0.0.1:3306/cloud_storage?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
spring.datasource.username=cloud_storage
spring.datasource.password=change-me

spring.jpa.hibernate.ddl-auto=update
spring.jpa.open-in-view=false

spring.servlet.multipart.max-file-size=1024MB
spring.servlet.multipart.max-request-size=2048MB

storage.root=storage

app.jwt.secret=change-me-to-a-long-random-secret
app.jwt.expire-hours=168
app.frontend-origin=https://your-domain.com
app.cors.allowed-origins=https://your-domain.com

app.admin.default-username=admin
app.admin.default-password=change-me
app.admin.default-email=admin@example.com

app.upload.session-ttl-hours=72
app.upload.cleanup-delay-ms=3600000
```

说明：

- `storage.root` 建议配置为独立数据盘路径，例如 `/data/cloud-storage/storage`。
- `app.frontend-origin` 用于生成分享链接。
- `app.cors.allowed-origins` 必须包含前端访问域名。
- `spring.jpa.hibernate.ddl-auto=update` 适合开发环境，生产环境建议改为 `validate` 并使用 Flyway/Liquibase。

## 数据库初始化

```sql
CREATE DATABASE cloud_storage DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'cloud_storage'@'%' IDENTIFIED BY 'strong-password';
GRANT ALL PRIVILEGES ON cloud_storage.* TO 'cloud_storage'@'%';
FLUSH PRIVILEGES;
```

首次启动后，JPA 会根据实体自动创建表结构。

## 构建后端

Windows：

```powershell
cd backend
.\mvnw.cmd clean package -DskipTests
```

Linux：

```bash
cd backend
./mvnw clean package -DskipTests
```

构建产物通常位于：

```text
backend/target/backend-0.0.1-SNAPSHOT.jar
```

启动：

```bash
java -jar backend-0.0.1-SNAPSHOT.jar
```

使用外部配置文件启动：

```bash
java -jar backend-0.0.1-SNAPSHOT.jar --spring.config.location=file:/data/cloud-storage/application.properties
```

## 构建前端

```bash
cd frontend
npm install
npm run build
```

构建产物：

```text
frontend/dist/
```

将 `frontend/dist` 部署到 Nginx 静态目录。

## Nginx 示例

```nginx
server {
    listen 80;
    server_name your-domain.com;

    client_max_body_size 2048m;

    root /var/www/cloud-storage;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        proxy_connect_timeout 60s;
        proxy_send_timeout 3600s;
        proxy_read_timeout 3600s;
    }
}
```

如果使用 HTTPS，建议通过 Certbot 或云厂商证书管理配置 TLS。

## systemd 示例

创建文件：

```text
/etc/systemd/system/cloud-storage.service
```

内容：

```ini
[Unit]
Description=Cloud Storage Backend
After=network.target mysql.service

[Service]
User=cloudstorage
WorkingDirectory=/opt/cloud-storage/backend
ExecStart=/usr/bin/java -jar /opt/cloud-storage/backend/backend-0.0.1-SNAPSHOT.jar --spring.config.location=file:/opt/cloud-storage/application.properties
Restart=always
RestartSec=5
Environment=TZ=Asia/Shanghai

[Install]
WantedBy=multi-user.target
```

启用服务：

```bash
sudo systemctl daemon-reload
sudo systemctl enable cloud-storage
sudo systemctl start cloud-storage
sudo systemctl status cloud-storage
```

查看日志：

```bash
journalctl -u cloud-storage -f
```

## 分片上传部署注意事项

当前分片上传流程：

1. 前端创建上传会话。
2. 前端按 `32MB` 默认大小切分文件。
3. 前端并发上传分片。
4. 后端保存分片到 `storage/tmp/uploads/{uploadId}`。
5. 前端通知后端完成。
6. 后端流式合并分片，写入正式存储并清理临时分片。

生产部署建议：

- `storage.root` 放到大容量 SSD 或独立数据盘。
- 确保存储盘至少能容纳上传中的临时分片和最终文件。
- 根据带宽调整前端并发数，默认并发为 `3`。
- Nginx 和后端代理超时时间需要覆盖大文件上传和合并耗时。
- 定期监控 `storage/tmp/uploads`，确认过期清理正常。

## 性能与容量建议

中小规模部署可以使用当前本地存储模式。若需要支持大量并发用户和 100GB 级文件，建议升级为对象存储架构：

- MinIO、AWS S3、阿里云 OSS、腾讯 COS、Cloudflare R2
- 前端通过预签名 URL 直传对象存储
- 后端只管理权限、签名、元数据和完成确认
- 上传热状态、限流、队列可放 Redis
- MySQL 只保存最终文件记录、分享记录和审计数据

## 安全检查清单

- 修改默认管理员密码
- 使用强随机 `app.jwt.secret`
- 不提交真实数据库密码和生产配置
- 开启 HTTPS
- 限制 MySQL 访问来源
- 设置服务器防火墙，只开放必要端口
- 定期备份数据库和文件存储目录
- 对上传目录做磁盘容量监控
- 生产环境不要使用默认 `Admin@123456`

## 验证命令

后端测试：

```bash
cd backend
./mvnw test
```

前端构建：

```bash
cd frontend
npm run build
```

接口健康检查：

```bash
curl http://127.0.0.1:8080/api/public/settings
```
