# VeilSU hello KPM — LKM 骨架

最小内核模块版 KPM 骨架，按 KSU LKM 的做法实现，原生适配 VeilSU。

## 这是什么

一个真正的可加载内核模块（`.ko`），不是 KernelPatch 的 `.kpm` 文件：

- `veilsu_hello.c`：`module_init` / `module_exit`，加载时打印版本，卸载时清理。还没有注册任何 hook，先跑通编、加载、卸载整条链路。
- `Kbuild`：`obj-m += veilsu_hello.o`。
- `Makefile`：照 `kernel/Makefile`（`kernelsu.ko` 的编法）做的 out-of-tree 编译，`KDIR` 指向准备好的内核目录。
- `.github/workflows/kpm-hello.yml`：CI 在 DDK 容器里给 `android17-6.18`（一加 15 / Android 17）编出 `veilsu_hello.ko`，上传为 artifact。

原生适配点：

- 目标 KMI 是 `android17-6.18`，和仓库 `veilsu.yml` 的 build-lkm 用的 DDK 镜像一致（`ghcr.io/ylarod/ddk-min:android17-6.18-20260828`）。
- 版本号对齐内核里 KPM 控制面的 ABI（`kernel/extension/kpm/bridge.h`，`VEILSU_KPM_ABI_VERSION == 2`）。
- 注意：`veilsu_kpm_bridge` 在 `kernelsu.ko` 内部，没有导出给别的模块，所以本模块不直接链接它。KPM 的 LOAD / UNLOAD / LIST / INFO / CONTROL / VERSION 仍走用户态（ksud / 管理器）经 `do_kpm()`。模块内要用的内核符号，自己在 `veilsu_hello_init()` 里解析、注册，在 `veilsu_hello_exit()` 里反向注销。

## CI 构建

推到 `veilsu-v1` 且改了 `kpm/veilsu-hello/**` 就会触发 `kpm-hello.yml`，产物是 `veilsu-hello-android17-6.18`（内含 `veilsu_hello.ko`），也可以在 Actions 里手动跑（workflow_dispatch）。

## 在一加 15 上测试

先把 `veilsu_hello.ko` 传到手机 `/sdcard/Download/`，不要加到开机自启，手动加载，出了问题重启就没了：

```sh
su -c 'insmod /sdcard/Download/veilsu_hello.ko'
su -c 'dmesg | grep veilsu-hello'
su -c 'lsmod | grep veilsu_hello'
```

能看到 `VeilSU KPM hello loaded, version 0.1.0` 就说明链路通了。再卸载验证退出路径：

```sh
su -c 'rmmod veilsu_hello'
su -c 'dmesg | grep veilsu-hello'
```

如果 `insmod` 被拒（比如 `Required key not available` 或 `Invalid module format`），是当前内核强制模块签名或 KMI 不匹配，不是骨架代码的问题，不要在主力机上 `--force` 强行加载。

## 下一步

骨架跑通后，在 `veilsu_hello_init()` 里加第一个真正的功能（hook 目标、要改的符号、参数），并在 `veilsu_hello_exit()` 里补对应的注销。
