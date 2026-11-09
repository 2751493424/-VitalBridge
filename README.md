# VitalBridge（脉迹）

> **[支持本项目](https://REPLACE-ME.invalid/donate.html)** · 捐赠完全自愿，不影响任何功能，也不换取任何权益。

面向三星 Galaxy Watch（Wear OS / One UI Watch）的健康数据同步与查看工具，由手机端、手表端和一个共享模块组成。

本仓库**只包含三端源码、资源与构建脚本**。三星 Health SDK、签名密钥、历史安装包、内部文档与调试产物均不包含，获取方式见下文。

## 边界

- **不是医疗器械**，不用于诊断、治疗或任何医疗决策；所有读数仅供个人记录与查看，不构成医疗建议。
- 不是三星官方产品，与 Samsung 无从属或授权关系。
- 本仓库**不提供任何开源许可证**，详见「权利声明」。

源码位于三个目录：`app/`（手机端）、`core/`（共享模块）、`wear/`（手表端）。

## 构建前置

- JDK 17、Android SDK 36（`compileSdk 36`，手机 `minSdk 29`、手表 `minSdk 30`）、Gradle 9.x（开发环境为 9.2.1）。本项目不带 Gradle Wrapper，请使用本机 Gradle。
- **两个三星 SDK AAR**（因授权原因不随仓库分发，请从 Samsung Health SDK 官方渠道自行获取对应版本）：

  | 放置位置 | 文件名 |
  | --- | --- |
  | `app/libs/` | `samsung-health-data-api-1.1.0.aar` |
  | `wear/libs/` | `samsung-health-sensor-api-1.4.1.aar` |

  文件名与 `app/build.gradle.kts`、`wear/build.gradle.kts` 里的 `files("libs/...")` 一致；若拿到的是其它版本，同步修改这两处。没有这两个文件无法通过依赖解析。

- **引导图（需自备）**：`app/src/main/res/drawable-nodpi/` 下的 `guide_*` 是操作步骤截图，**涉及第三方应用界面版权，不随仓库分发**。缺少这些文件时，引导页相关的 `R.drawable.guide_*` 引用会导致编译失败；如需完整构建，请自行准备同名图片，或把引导页里对应引用换成自己的图。文件名清单见 `drawable-nodpi/README.txt`。

- **签名（可选）**：构建脚本会读取仓库根目录的 `keystore.properties`，字段为 `storeFile` / `storePassword` / `keyAlias` / `keyPassword`。文件缺失时自动回退到 debug 签名，构建不会因此失败。**密钥文件与 `keystore.properties` 都不要放进版本库**（已在 `.gitignore` 中排除）。

```bash
gradle :app:assembleDebug
gradle :wear:assembleDebug
gradle :app:assembleRelease
```

正式包只打 `arm64-v8a`；在 x86 模拟器上仅预览 UI 时可加 `-PpreviewNoNative` 剔除 `.so`，**此开关不可用于出包**。

## 运行时服务地址（已占位）

本仓库不含任何真实的服务端点、对象存储桶 ID 或作者联系邮箱。公开源码中的下列位置均为占位值，请按自己的部署替换后再构建：

| 位置 | 占位值 | 说明 |
| --- | --- | --- |
| `app/.../AnnouncementRepository.kt` → `URLS` | `https://REPLACE-ME*.invalid/announcement.json` | 公告 JSON 的 3 个候选源；含 `REPLACE-ME` 的地址会被自动跳过，不会发起请求 |
| `app/.../MainActivity.kt` → `LEGAL_ONLINE_URL` | `https://REPLACE-ME.invalid/legal/index.html` | 在线法律页 |
| `app/.../WatchOta.kt` → `BASE` / `TRUSTED_HOSTS` | `https://REPLACE-ME.invalid/` / `REPLACE-ME.invalid` | 手表包清单地址与下载主机白名单，**两处必须一起改** |
| `wear/.../WatchOtaClient.kt` → `BASE` | 同上 | 手表端自更新地址，需与手机端一致 |
| `app/.../Stats.kt` → `BASE_URL` | 空串 | 匿名统计；留空即整个模块静默不工作（默认关闭） |
| `app/.../MainActivity.kt` → `DONATE_URL` | `https://REPLACE-ME.invalid/donate.html` | 捐赠页；本仓库根目录有 `donate.html`，可部署到你自己的静态托管 |
| `app/src/main/assets/legal/*.txt` | `contact@example.com` | 四语言隐私政策里的联系邮箱与公告地址 |

`.invalid` 是 RFC 2606 保留的顶级域，保证不会被解析到真实主机。仓库中保留的非私有 URL 只有：Android XML 命名空间 `schemas.android.com`、Gradle 依赖仓库 `jitpack.io`，以及注释里用于解释 URL 解析规则的示例域名（`example.org` / `attacker.example`）。

## 数据说明

健康数据仅保存在设备本地与用户主动导出的文件中；匿名使用统计默认关闭，开启后也只发送应用版本、机型、系统版本、语言与功能使用情况，不含任何健康数值或设备标识。应用内提供完整的多语言隐私政策与免责声明（见 `app/src/main/assets/legal/`）。

## 权利声明

© 2026 VitalBridge 项目作者。**保留所有权利（All Rights Reserved）**。

本仓库内容仅用于技术展示与代码审阅。未经版权所有者书面许可，不得复制、修改、合并、发布、分发、再许可或用于商业用途。仓库中不存在 `LICENSE` 文件，即表示未授予任何开源许可。

仓库内出现的第三方 SDK 名称、接口与文档权利归各自所有者所有；本仓库不包含任何第三方 SDK 二进制。

## 免责声明

代码按「现状」提供，不附带任何明示或默示担保，包括但不限于准确性、完整性与特定用途适用性。作者不对使用本代码或依赖其展示数据所产生的任何后果承担责任。
