// SPDX-License-Identifier: GPL-2.0
#ifndef __KSU_H_APK_V2_SIGN
#define __KSU_H_APK_V2_SIGN

#include <linux/types.h>


/*
 * Upper bound for the first DER-encoded certificate of an APK v2 signing
 * block. A 1 KiB ceiling used to reject managers signed with 4096-bit RSA
 * keys (their certificates are ~1.2 KiB), so budget for large keys and
 * heavily extended certificates while keeping the read bounded.
 *
 * Shared by is_manager_apk() and the dynamic-manager registry so a
 * cert_size accepted at registration can always be verified later.
 *
 */

#define KSU_APK_CERT_MAX_SIZE 8192U


bool is_manager_apk(char *path);
bool ksu_check_apk_signature(char *path, unsigned int size, const char *hash);
int get_pkg_from_apk_dir_path(char *pkg, const char *path);

#endif
