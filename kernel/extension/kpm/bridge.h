/* SPDX-License-Identifier: GPL-2.0 */
#ifndef _VEILSU_KPM_BRIDGE_H_
#define _VEILSU_KPM_BRIDGE_H_

/* Kernel-only ABI v2, mirrored in KernelPatch patch/veilsu/supercall.h.
 * op 1..7 are the existing VEILSU_KPM_* extension operations. Op 8 is an
 * optional event-aware file load for boot-stage modules.
 * LOAD(path,args,user_reserved), UNLOAD(name,user_reserved), NUM(),
 * LIST(kernel_buf,size), INFO(name,kernel_buf,size),
 * CONTROL(name,args,user_output,size), COMMANDS(name,kernel_buf,size),
 * VERSION(kernel_buf,size).
 * Op 9 reports the host identity (VEILSU_KPM_HOST, defined in
 * uapi/extension.h) as BOOT_IMAGE or MODULE.
 */
/* Public framing tags: ALL must match. These are not authentication. */
#define VEILSU_KPM_SUPERCALL_MAGIC 0x53554b504d000002UL
#define VEILSU_KPM_SUPERCALL_MAGIC2 0x4b504d4252494447UL
#define VEILSU_KPM_SUPERCALL_MAGIC3 0x53554b494e544552UL
/* x6=magic2, x7=magic3, x8=ABI version; x9..x30 reserved (zero).
 * Internal synthetic pt_regs only; the userspace extension ABI is unchanged.
 * Event-aware loading is optional so newer ksud can fall back to LOAD on
 * older KernelPatch builds that return -EOPNOTSUPP for op 8.
 */
#define VEILSU_KPM_ABI_VERSION 2
#define VEILSU_KPM_PROBE 0
#define VEILSU_KPM_OUTPUT_MAX 8192

long veilsu_kpm_bridge(unsigned long op, unsigned long a1, unsigned long a2,
                       unsigned long a3, unsigned long a4);
#endif
