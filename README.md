# Cloud Storage

Cloud Storage 是一个前后端分离的网盘系统，后端基于 Spring Boot + MySQL，前端基于 Vue 3 + Vite。项目实现了账号体系、文件管理、分享链接、直链下载、管理员管理，以及面向大文件的分片上传和断点续传。

## 功能特性

- 用户注册、登录、图形验证码、预留邮箱
- 用户资料与头像管理
- 文件上传、拖拽上传、下载、预览
- 大文件分片上传、断点续传、暂停、继续、取消、实时速度和剩余时间
- 文件夹创建、重命名、移动、复制、删除
- 文件夹分享和文件分享，支持可选提取码
- 文件直链、分享下载次数、文件下载次数统计
- 图片、音频、视频、PDF、文本预览
- ZIP 在线解压
- 管理员查看用户、管理用户文件、查看登录/文件操作审计
- 系统设置：站点名称、注册/登录开关、头像修改开关

## 技术栈

后端：

- Java 21
- Spring Boot 4
- Spring Security + JWT
- Spring Data JPA
- MySQL
- 本地文件存储，预留对象存储改造空间

前端：

- Vue 3
- Vite
- Lucide Vue 图标
- 原生 Fetch / XMLHttpRequest 上传进度

## 目录结构

```text
cloud_storage/
  backend/    Spring Boot API、数据库模型、文件存储与上传逻辑
  frontend/   Vue 3 + Vite 网页端
```

## 本地开发

### 1. 准备数据库

创建 MySQL 数据库和用户：

```sql
CREATE DATABASE cloud_storage DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'cloud_storage'@'%' IDENTIFIED BY 'change-me';
GRANT ALL PRIVILEGES ON cloud_storage.* TO 'cloud_storage'@'%';
FLUSH PRIVILEGES;
```

复制示例配置并按本地环境修改：

```powershell
Copy-Item backend\src\main\resources\application-example.properties backend\src\main\resources\application.properties
```

重点修改：

```properties
spring.datasource.url=jdbc:mysql://127.0.0.1:3306/cloud_storage?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
spring.datasource.username=cloud_storage
spring.datasource.password=change-me
app.jwt.secret=change-me-to-a-long-random-secret
```

### 2. 启动后端

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

默认后端地址：

```text
http://localhost:8080
```

### 3. 启动前端

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

默认前端地址：

```text
http://localhost:5173
```

## 默认管理员

后端首次启动时会自动创建默认管理员：

```text
用户名：admin
密码：Admin@123456
```

生产环境必须修改以下配置：

```properties
app.admin.default-username=
app.admin.default-password=
app.admin.default-email=
app.jwt.secret=
```

## 上传与存储

当前项目支持两种上传链路：

- 普通接口：`/api/files/upload`
- 分片上传：`/api/files/uploads/**`

前端文件上传主流程已使用分片上传。默认分片大小为 `32MB`，前端默认并发数为 `3`，支持暂停、继续、取消和断点续传。

文件默认存储在：

```text
backend/storage/
```

临时分片存储在：

```text
backend/storage/tmp/uploads/
```

后端会定时清理过期上传任务，默认上传会话有效期为 72 小时。

## 验证命令

后端：

```powershell
cd backend
.\mvnw.cmd test
```

前端：

```powershell
cd frontend
npm.cmd run build
```

## 部署

部署说明见 [DEPLOYMENT.md](DEPLOYMENT.md)。

## 生产建议

- 不要将真实 `application.properties`、数据库密码、JWT 密钥提交到 GitHub。
- 生产环境建议关闭 `spring.jpa.hibernate.ddl-auto=update`，改用 Flyway/Liquibase 管理表结构。
- 大规模并发上传建议接入 MinIO、S3、阿里云 OSS、腾讯 COS 等对象存储，让前端直传对象存储。
- 当前本地存储方案适合开发、中小规模部署；上千用户并发大文件上传需要对象存储、Redis 限流和专门压测。
