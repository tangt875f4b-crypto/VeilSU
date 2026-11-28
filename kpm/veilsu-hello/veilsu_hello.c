// SPDX-License-Identifier: GPL-2.0
/*
 * VeilSU hello KPM — minimal LKM-style kernel module skeleton.
 *
 * This is a real loadable kernel module (.ko), built out-of-tree with the
 * kernel build system exactly like kernelsu.ko (see kernel/Makefile and
 * kernel/Kbuild): `make -C $(KDIR) M=$(PWD) modules`.
 *
 * Native VeilSU adaptation:
 * - Target KMI is android17-6.18 (OnePlus 15, Android 17, arm64 GKI).
 * - The module speaks the same KPM ABI version as the in-kernel KPM
 *   control surface (kernel/extension/kpm/kpm.c, veilsu_kpm_bridge,
 *   VEILSU_KPM_ABI_VERSION == 2). It does not link against
 *   veilsu_kpm_bridge directly: that symbol lives inside kernelsu.ko
 *   and is not exported to other modules. Interaction with the VeilSU
 *   KPM loader (LOAD / UNLOAD / LIST / INFO / CONTROL / VERSION) stays
 *   on the userspace side (ksud / manager), via do_kpm().
 * - The hook point for real functionality is veilsu_hello_init():
 *   register hooks / resolve kernel symbols there, and undo everything
 *   in veilsu_hello_exit(). Nothing is registered yet on purpose: the
 *   first milestone is proving the build -> load -> unload pipeline.
 *
 * Loading on device (root shell, do NOT load at boot yet):
 *   su -c 'insmod /sdcard/Download/veilsu_hello.ko'
 *   su -c 'dmesg | grep veilsu-hello'
 *   su -c 'lsmod | grep veilsu_hello'
 * Unloading:
 *   su -c 'rmmod veilsu_hello'
 *
 * If insmod is refused (e.g. "Required key not available" or
 * "Invalid module format"), the running kernel enforces module
 * signatures or a different vermagic — that is a signing / KMI
 * mismatch, not a bug in this skeleton. Do not force it with
 * --force on a daily driver.
 */

#include <linux/module.h>
#include <linux/kernel.h>
#include <linux/init.h>
#include <linux/version.h>
#include <linux/utsname.h>

#define VEILSU_HELLO_VERSION "0.1.0"
/* Keep in step with VEILSU_KPM_ABI_VERSION in kernel/extension/kpm/bridge.h */
#define VEILSU_HELLO_KPM_ABI 2

static int __init veilsu_hello_init(void)
{
	pr_info("veilsu-hello: VeilSU KPM hello loaded, version %s (KPM ABI v%d, kernel %s)\n",
		VEILSU_HELLO_VERSION, VEILSU_HELLO_KPM_ABI,
		utsname()->release);
	/* Hook point: register hooks / resolve symbols here. */
	return 0;
}

static void __exit veilsu_hello_exit(void)
{
	/* Hook point: unregister everything registered in init, in reverse. */
	pr_info("veilsu-hello: VeilSU KPM hello unloaded, version %s\n",
		VEILSU_HELLO_VERSION);
}

module_init(veilsu_hello_init);
module_exit(veilsu_hello_exit);

MODULE_LICENSE("GPL v2");
MODULE_AUTHOR("VeilSU");
MODULE_DESCRIPTION("VeilSU KPM hello — minimal LKM-style skeleton, natively adapted to VeilSU");
MODULE_VERSION(VEILSU_HELLO_VERSION);
