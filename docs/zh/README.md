# VeilSU
<img align='right' src='../VeilSU.svg' width='220px' alt="VeilSU logo">

**简体中文** [English](.././README.md) |

一个 Android 上基于内核的 root 方案，fork 自 [SukiSU Nova](https://github.com/SukiSU-Nova/SukiSU-Nova)（其由 [`tiann/KernelSU`](https://github.com/tiann/KernelSU) 进化而来）。

VeilSU v1 有意做小：只留模块 + su 授权，不要 Zygisk——无 zygote 注入、无 Zygisk 模块，可观测面最小。

[![最新发行](https://img.shields.io/github/v/release/tangt875f4b-crypto/VeilSU?label=Release&logo=github)](https://github.com/tangt875f4b-crypto/VeilSU/releases/latest)
[![协议: GPL 3.0 or later](https://img.shields.io/badge/License-GPL%203.0%20or%20later-orange.svg?logo=gnu)](https://www.gnu.org/licenses/gpl-3.0.html)
[![GitHub 协议](https://img.shields.io/github/license/tangt875f4b-crypto/VeilSU?logo=gnu)](/LICENSE)

## 特性

1. 基于内核的 `su` 和权限管理。
2. App Profile：把 Root 权限关进笼子里。
3. KPM 支持。
4. v1 无 Zygisk——不带 zygote 注入。模块按普通模块运行（脚本 + system 文件），Zygisk 模块的 zygote 代码不运行。
5. 主题商店：浏览、安装、投稿主题，支持作者授权与审核。
6. 内核扩展中心：CPU/vvar 伪装、AVC 日志伪装、版本伪装、SUSFS 与挂载视图隐藏，集中在一个入口。
7. 内置 SUSFS 管理工具，支持一键配置 Root 隐藏。
8. Miuix / Material 3 Expressive 双界面；支持 Miuix 开屏、模块横幅与排序、临时 Root 授权与无内核预览。

## 兼容状态

- VeilSU 支持 GKI 2.0 设备（内核版本 5.10 以上）。
- 旧内核也可兼容（最低 4.14+），但需要自行编译内核。
- 通过更多反向移植，可支持 3.x 内核（3.4–3.18）。
- 目前支持架构：`arm64-v8a`、`armeabi-v7a (bare)`、`X86_64`、`riscv64`。
- **注意**：本项目自 `tiann/KernelSU` 分叉后独立演进，兼容性以本仓库实测为准，不再等同于 KernelSU 官方支持范围。

## 内核兼容性状态

| 场景 | 方式 | 说明 |
|------|------|------|
| GKI 设备（内核 5.10+） | 推荐使用LKM模式
| 非 GKI / 旧内核（3.0+） | 手动编译内核 | 请参考网站集成指南
| 需要使用 KPM 功能 | 需开启 `CONFIG_KPM=y` | 见下方「KPM 支持」 |


## KPM 支持

基于 [KernelPatch](https://github.com/bmax121/KernelPatch) 开发，移除了与 KernelSU 重复的功能，仅保留 KPM 支持。

**当前状态**：
- ✅ 基础 KPM 加载已可用（需开启 `CONFIG_KPM=y`）
- 🚧 APatch 兼容层仍在补齐（部分功能尚未合并）
- ⚠️ Non-GKI 设备需额外开启 `CONFIG_KALLSYMS=y` 和 `CONFIG_KALLSYMS_ALL=y`
- ⚠️ 内核低于 4.19 需从 4.19 的 `set_memory.h` 反向移植

**开源仓库**：[ShirkNeko/SukiSU_KernelPatch_patch](https://github.com/ShirkNeko/SukiSU_KernelPatch_patch)  
**KPM 模板**：[udochina/KPM-Build-Anywhere](https://github.com/udochina/KPM-Build-Anywhere)

## 故障排除

1. **KPM 加载失败？**  
   确认内核已开启 `CONFIG_KPM=y`；Non-GKI 设备还需 `CONFIG_KALLSYMS=y` 和 `CONFIG_KALLSYMS_ALL=y`。

2. **管理器无法识别 root？**  
   确认刷入的内核与管理器版本匹配，并检查 `su` 是否在 PATH 中。

3. **内核编译报错？**  
   参考 [`guide/how-to-integrate.md`](guide/how-to-integrate.md)，并确认内核版本满足最低要求（4.14+）。

## 参与翻译

要将 VeilSU 翻译成您的语言，或完善现有的翻译，请直接提 PR——VeilSU 暂无 Weblate 项目。

## 许可证

- 目录 `kernel` 下所有文件为 [GPL-2.0-only](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html)。
- VeilSU v1 换了新的面纱图标。上游 SukiSU Nova 的图标文件（动漫人物图片，怡子曰曰绘制，版权明风OuO所有）不再随附；[`LICENSE_icon_English`](./LICENSE_icon_English) 与 [`LICENSE_icon_SC`](./LICENSE_icon_SC) 保留，用于注明上游图标作品的授权。
- 除上述文件及目录的其他部分均为 [GPL-3.0-or-later](https://www.gnu.org/licenses/gpl-3.0.html)。

## 赞助

- [tangt875f4b-crypto](https://github.com/tangt875f4b-crypto)（VeilSU 维护者）
- [weishu](https://github.com/sponsors/tiann)（KernelSU 作者）

## 鸣谢

- [SukiSU Nova](https://github.com/SukiSU-Nova/SukiSU-Nova)（ShirkNeko）：本 fork 的上游
- [KernelSU](https://github.com/tiann/KernelSU)：上游
- [/xxksu](https://github.com/backslashxx/KernelSU) 内核兼容性支持参考
- [susfs](https://gitlab.com/simonpunk/susfs4ksu)：隐藏内核补丁以及用户空间模组的 KernelSU 附件
- [KernelPatch](https://github.com/bmax121/KernelPatch)：KernelPatch 是内核模块 APatch 实现的关键部分

欢迎提交 PR；问题反馈请开 [Issue](https://github.com/tangt875f4b-crypto/VeilSU/issues)。

<details>
<summary>KernelSU 的鸣谢</summary>

- [kernel-assisted-superuser](https://git.zx2c4.com/kernel-assisted-superuser/about/)：KernelSU 的灵感。
- [Magisk](https://github.com/topjohnwu/Magisk)：强大的 root 工具箱。
- [genuine](https://github.com/brevent/genuine/)：apk v2 签名验证。
- [Diamorphine](https://github.com/m0nad/Diamorphine)：一些 rootkit 技巧。
</details>
