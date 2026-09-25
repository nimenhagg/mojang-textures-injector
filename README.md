# MojangTexturesInjector

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Paper%20%7C%20Purpur-brightgreen.svg)]()
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21%2B-orange.svg)]()

一个轻量、优雅且高效的 Minecraft Paper 服务端伴生插件。专为**离线模式（online-mode=false）**服务器设计，让正版玩家在离线服中也能**原汁原味显示官方皮肤与各类官方披风（Cape）**！

---

## 💡 解决的痛点

在离线模式（盗版/离线服）下：
1. 原版客户端不会向 Mojang 验证身份，服务端默认下发的 GameProfile 没有合法的官方数字签名。
2. 常见的皮肤插件（如 SkinsRestorer 免费版）通常不提供官方披风的生成与加载（披风往往需要额外付费 API）。
3. 很多拥有**创始人披风、迁移披风、Vanilla 披风、15周年披风**的正版玩家进服后无法佩戴披风，缺乏正版认同感。

**MojangTexturesInjector** 从底层协议与 Paper 原生 API 切入，异步向 Mojang SessionServer 拉取携带 Mojang 官方私钥签名的完整 `textures` 属性，直接注入到玩家的 `PlayerProfile` 中，原版客户端验证数字签名通过后，即可直接完整渲染出官方正版皮肤与披风！

---

## ✨ 核心特性

- ⚡ **异步非阻塞**：所有网络请求在后台异步线程池处理，零卡顿，不阻塞服务器主线程。
- 🛡️ **安全联动**：完美兼容 `LoginSecurity` 等登录插件，玩家通过密码校验后才注入属性，防止他人按名字冒用。
- 🗄️ **智能缓存**：内置内存与磁盘双层缓存（默认 24 小时 TTL），有效防止频繁触发 Mojang 官方 API 限流。
- 🌐 **代理支持**：支持配置 HTTP 代理服务器，无惧国内机器连接 Mojang SessionServer 偶发的超时问题。
- 🔄 **热刷新与指令支持**：支持玩家自主执行指令刷新，或管理员强制刷新。

---

## 🛠️ 安装与使用

### 安装
1. 下载最新的 `MojangTexturesInjector-1.0.0.jar`。
2. 放入服务器的 `plugins/` 目录中。
3. 重启服务器或使用插件管理器载入。

### 指令与权限

| 指令 | 描述 | 默认权限 | 权限节点 |
|---|---|---|---|
| `/cape refresh` 或 `/mojangtextures refresh` | 重新从 Mojang 刷新自己的皮肤与披风 | 所有玩家 | `mojangtextures.use` |
| `/mojangtextures refresh <玩家名>` | 强制从 Mojang 刷新指定玩家的纹理 | 管理员 (OP) | `mojangtextures.admin` |
| `/mojangtextures reload` | 重载插件配置文件与代理设置 | 管理员 (OP) | `mojangtextures.admin` |

---

## ⚙️ 配置文件说明 (`config.yml`)

```yaml
# MojangTexturesInjector 配置文件

proxy:
  # 是否开启 HTTP 代理（国内服务器建议配置）
  enabled: false
  host: "127.0.0.1"
  port: 10808

network:
  # 请求 Mojang API 超时时间（秒）
  timeout-seconds: 10

cache:
  # 玩家正版纹理本地缓存时长（小时），避免被 Mojang 429 限流
  ttl-hours: 24

settings:
  # 是否在玩家进服时就注入（若为 false，则等待 LoginSecurity 登录成功后再注入）
  inject-on-join: false
  # 注入成功后是否向玩家发送聊天栏提示
  notify-player: true
```

---

## 🔨 源码编译

```bash
git clone https://github.com/nimenhagg/mojang-textures-injector.git
cd mojang-textures-injector
mvn clean package
```
编译产物位于 `target/mojang-textures-injector-1.0.0.jar`。

---

## 📄 开源协议与使用规范

本项目基于 **GNU General Public License v3.0 (GPLv3)** 开源，并补充以下约定：

### 1. 允许的行为 ✅
- **服务器商用**：允许任何个人、团队将本插件免费部署在任何**盈利或非盈利**的 Minecraft 服务器中（包括含有内购、赞助的商业服）。
- **学习与自用修改**：允许自行修改源码用于自己的服务器，私下自用无需公开修改后的源码。
- **开源二次分发**：允许基于本项目进行二次开发与传播，但**衍生作品必须同样以 GPLv3 协议完全开源**。

### 2. 严格禁止的行为 ❌
- **禁止直接转售/倒卖**：严禁任何个人或组织将本插件（包括其编译后的 .jar、源代码或微调修改版）作为独立商品、付费资源或捆绑包进行直接销售、付费下载或设置付费门槛。
- **保留原作者署名**：在任何分发版本中均须保留原作者版权信息与开源地址。
