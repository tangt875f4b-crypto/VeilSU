# VeilSU
<img align='right' src='VeilSU.svg' width='220px' alt="VeilSU logo">

**English** [简体中文](./zh/README.md) |

A kernel-based root solution for Android, forked from [SukiSU Nova](https://github.com/SukiSU-Nova/SukiSU-Nova) (itself evolved from [`tiann/KernelSU`](https://github.com/tiann/KernelSU)).

VeilSU v1 is minimal on purpose: modules + su authorization only, no Zygisk — no zygote injection, no Zygisk modules — for the smallest observable surface.

[![Latest Release](https://img.shields.io/github/v/release/tangt875f4b-crypto/VeilSU?label=Release&logo=github)](https://github.com/tangt875f4b-crypto/VeilSU/releases/latest)
[![License: GPL 3.0 or later](https://img.shields.io/badge/License-GPL%203.0%20or%20later-orange.svg?logo=gnu)](https://www.gnu.org/licenses/gpl-3.0.html)
[![GitHub License](https://img.shields.io/github/license/tangt875f4b-crypto/VeilSU?logo=gnu)](/LICENSE)

## Features

1. Kernel-based `su` and permission management.
2. [App Profile](https://sukisu.org/guide/app-profile): keep root access in a cage.
3. KPM support.
4. No Zygisk in v1 — VeilSU ships no zygote injection. Modules run as plain modules (scripts + system files); Zygisk modules' zygote code does not run.
5. Theme Store: browse, install and submit themes, with author authorization and review.
6. Kernel Extension Center: CPU/vvar spoofing, AVC log spoofing, version spoofing, SUSFS and mount view hiding — all in one place.
7. Built-in SUSFS management tools with one-tap root hiding configuration.
8. Dual UI: Miuix / Material 3 Expressive; supports Miuix splash screen, module banners and sorting, temporary root grant, and kernel-free preview.

## Compatibility

- VeilSU supports GKI 2.0 devices (kernel version 5.10 and above).
- Older kernels are also compatible (minimum 4.14+), but you need to compile the kernel yourself.
- With additional backports, 3.x kernels (3.4–3.18) can be supported.
- Currently supported architectures: `arm64-v8a`, `armeabi-v7a (bare)`, `X86_64`, `riscv64`.
- **Note**: This project has evolved independently since forking from `tiann/KernelSU`; compatibility is based on testing in this repository and no longer equals the official KernelSU support scope.

## Kernel Compatibility Status

| Scenario | Method | Notes |
|------|------|------|
| GKI devices (kernel 5.10+) | LKM mode recommended | |
| Non-GKI / old kernels (3.0+) | Compile the kernel manually | Please refer to the integration guide on the website |
| KPM features required | Requires `CONFIG_KPM=y` | See "KPM Support" below |


## KPM Support

Developed based on [KernelPatch](https://github.com/bmax121/KernelPatch), with features duplicating KernelSU removed — only KPM support is kept.

**Current status**:
- ✅ Basic KPM loading is available (requires `CONFIG_KPM=y`)
- 🚧 The APatch compatibility layer is still being completed (some features are not yet merged)
- ⚠️ Non-GKI devices additionally require `CONFIG_KALLSYMS=y` and `CONFIG_KALLSYMS_ALL=y`
- ⚠️ Kernels below 4.19 need `set_memory.h` backported from 4.19

**Open-source repository**: [ShirkNeko/SukiSU_KernelPatch_patch](https://github.com/ShirkNeko/SukiSU_KernelPatch_patch)  
**KPM template**: [udochina/KPM-Build-Anywhere](https://github.com/udochina/KPM-Build-Anywhere)

## Troubleshooting

1. **KPM fails to load?**  
   Make sure the kernel has `CONFIG_KPM=y` enabled; Non-GKI devices also need `CONFIG_KALLSYMS=y` and `CONFIG_KALLSYMS_ALL=y`.

2. **The manager doesn't recognize root?**  
   Make sure the flashed kernel matches the manager version, and check whether `su` is in PATH.

3. **Kernel compilation errors?**  
   Refer to [`guide/how-to-integrate.md`](guide/how-to-integrate.md), and make sure the kernel version meets the minimum requirement (4.14+).

## Contributing Translations

To translate VeilSU into your language, or improve existing translations, please open a PR — VeilSU has no Weblate project yet.

## License

- All files under the `kernel` directory are licensed under [GPL-2.0-only](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html).
- VeilSU v1 ships a new veil icon. The upstream SukiSU Nova icon files (anime character images, drawn by 怡子曰曰 (Yizi Yueyue), copyright owned by 明风OuO (Mingfeng OuO)) are no longer shipped; [`LICENSE_icon_English`](./LICENSE_icon_English) and [`LICENSE_icon_SC`](./LICENSE_icon_SC) are kept for attribution of that upstream artwork.
- All other parts, except the files and directories mentioned above, are licensed under [GPL-3.0-or-later](https://www.gnu.org/licenses/gpl-3.0.html).

## Sponsors

- [tangt875f4b-crypto](https://github.com/tangt875f4b-crypto) (maintainer of VeilSU)
- [weishu](https://github.com/sponsors/tiann) (author of KernelSU)

## Acknowledgements

- [SukiSU Nova](https://github.com/SukiSU-Nova/SukiSU-Nova) (ShirkNeko): upstream this fork is based on
- [KernelSU](https://github.com/tiann/KernelSU): upstream
- [/xxksu](https://github.com/backslashxx/KernelSU): reference for kernel compatibility support
- [susfs](https://gitlab.com/simonpunk/susfs4ksu): KernelSU addon with hiding kernel patches and userspace modules
- [KernelPatch](https://github.com/bmax121/KernelPatch): KernelPatch is a key part of the kernel module implementation in APatch

PRs are welcome; for issue reports please open an [Issue](https://github.com/tangt875f4b-crypto/VeilSU/issues).

<details>
<summary>KernelSU acknowledgements</summary>

- [kernel-assisted-superuser](https://git.zx2c4.com/kernel-assisted-superuser/about/): inspiration for KernelSU.
- [Magisk](https://github.com/topjohnwu/Magisk): the powerful root toolbox.
- [genuine](https://github.com/brevent/genuine/): apk v2 signature verification.
- [Diamorphine](https://github.com/m0nad/Diamorphine): some rootkit tricks.
</details>
