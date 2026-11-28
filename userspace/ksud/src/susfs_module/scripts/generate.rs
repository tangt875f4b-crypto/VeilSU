//! Full per-stage script generators.
//!
//! Each public function in this module assembles one of the four
//! boot-stage scripts (`service.sh`, `post-fs-data.sh`,
//! `post-mount.sh`, `boot-completed.sh`) by composing snippets from
//! the other [`super`] files together with `ModuleConfig` data.

use std::fmt::Write as FmtWrite;

use crate::susfs_module::consts::{DEFAULT_BUILD_TIME, DEFAULT_UNAME, shell_quote};
use crate::susfs_module::scripts::{cleanup, hide_bl, prelude};

/// Return `true` when [`generate_service_script`] has at least one SuSFS
/// setting to apply at service time.
///
/// `execute_in_post_fs_data` is honored here so the same condition can be
/// used for the "uname / build_time" branch.
#[allow(clippy::too_many_arguments)]
fn should_configure_in_service(
    sus_paths: &[String],
    sus_loop_paths: &[String],
    sus_maps: &[String],
    kstat_configs: &[String],
    add_kstat_paths: &[String],
    cmdline_or_bootconfig_path: &str,
    execute_in_post_fs_data: bool,
    uname_value: &str,
    build_time_value: &str,
) -> bool {
    !sus_paths.is_empty()
        || !sus_loop_paths.is_empty()
        || !sus_maps.is_empty()
        || !kstat_configs.is_empty()
        || !add_kstat_paths.is_empty()
        || !cmdline_or_bootconfig_path.is_empty()
        || (!execute_in_post_fs_data
            && (uname_value != DEFAULT_UNAME || build_time_value != DEFAULT_BUILD_TIME))
}

// ── shared shell fragments ───────────────────────────────────────────────────

/// Append one `ksud susfs <subcommand> <path>` invocation for commands whose
/// kernel handler resolves the target immediately (`kern_path()`), skipping
/// paths that do not exist yet.
///
/// The post-fs-data pass runs before credential-encrypted storage and the
/// FUSE `/sdcard` view are available, so the existence check keeps the early
/// log readable and lets the service / boot-completed passes add the path
/// once it appears. Latest SuSFS can also flag an existing FUSE inode through
/// `add-sus-path`, but app data is registered with `add-sus-path-loop`, which
/// is handled by [`append_loop_path_commands`] and has no existence check.
fn append_path_command(s: &mut String, subcommand: &str, path: &str, label: &str) {
    let quoted = shell_quote(path);
    let escaped = path.replace('\'', "'\\''");
    let _ = writeln!(
        s,
        "if [ -e {quoted} ]; then\n  if /data/adb/ksud susfs {subcommand} {quoted} >> \"$LOG_FILE\" 2>&1; then\n    echo \"$(get_current_time): {label}: {escaped}\" >> \"$LOG_FILE\"\n  else\n    echo \"$(get_current_time): {label}失败: {escaped}\" >> \"$LOG_FILE\"\n  fi\nelse\n  echo \"$(get_current_time): {label}跳过（路径尚不存在）: {escaped}\" >> \"$LOG_FILE\"\nfi"
    );
}

/// Append a path-based SuSFS section (`add-sus-path`, `add-sus-map`,
/// `add-sus-kstat`).
fn append_path_commands(
    s: &mut String,
    header: &str,
    subcommand: &str,
    label: &str,
    paths: &[String],
) {
    if paths.is_empty() {
        return;
    }
    let _ = writeln!(s, "\n# {header}");
    for path in paths {
        append_path_command(s, subcommand, path, label);
    }
    s.push('\n');
}

/// Append one `add-sus-path-loop` invocation.
///
/// The kernel does not resolve the target when this command runs: it only
/// stores the pathname, then flags the matching inode later when an app
/// process is spawned and marked umounted. The target may therefore not
/// exist yet, so no `[ -e ]` guard is emitted (the upstream CLI explicitly
/// skips that check for the loop variant too).
fn append_loop_path_command(s: &mut String, path: &str, label: &str) {
    let quoted = shell_quote(path);
    let escaped = path.replace('\'', "'\\''");
    let _ = writeln!(
        s,
        "if /data/adb/ksud susfs add-sus-path-loop {quoted} >> \"$LOG_FILE\" 2>&1; then\n  echo \"$(get_current_time): {label}: {escaped}\" >> \"$LOG_FILE\"\nelse\n  echo \"$(get_current_time): {label}失败: {escaped}\" >> \"$LOG_FILE\"\nfi"
    );
}

/// Append the `sus_path_loop` section.
fn append_loop_path_commands(s: &mut String, header: &str, label: &str, paths: &[String]) {
    if paths.is_empty() {
        return;
    }
    let _ = writeln!(s, "\n# {header}");
    for path in paths {
        append_loop_path_command(s, path, label);
    }
    s.push('\n');
}

/// Append the `add-sus-kstat-statically` entries. Each config is
/// `path|ino|dev|...`; SuSFS stats the path, so skip entries whose path is
/// not available yet.
fn append_static_kstat_commands(s: &mut String, configs: &[String]) {
    if configs.is_empty() {
        return;
    }
    s.push_str("\n# 添加Kstat静态配置\n");
    for config in configs {
        let parts: Vec<&str> = config.split('|').collect();
        if parts.len() >= 13 {
            let path = parts[0];
            let params = parts[1..].join("' '");
            let quoted = shell_quote(path);
            let escaped = path.replace('\'', "'\\''");
            let _ = writeln!(
                s,
                "if [ -e {quoted} ]; then\n  if /data/adb/ksud susfs add-sus-kstat-statically {quoted} '{params}' >> \"$LOG_FILE\" 2>&1; then\n    echo \"$(get_current_time): 添加Kstat静态配置: {escaped}\" >> \"$LOG_FILE\"\n  else\n    echo \"$(get_current_time): 添加Kstat静态配置失败: {escaped}\" >> \"$LOG_FILE\"\n  fi\nelse\n  echo \"$(get_current_time): 添加Kstat静态配置跳过（路径尚不存在）: {escaped}\" >> \"$LOG_FILE\"\nfi"
            );
        }
    }
    s.push('\n');
}

/// Apply the priority / sus_path UID filters. They only depend on
/// `/data/system/packages.list`, so they are safe to apply early.
fn append_uid_filter_commands(
    s: &mut String,
    priority_uid_filter: &str,
    sus_path_uid_filter: &str,
) {
    if !priority_uid_filter.trim().is_empty() {
        let quoted = shell_quote(priority_uid_filter);
        let _ = writeln!(
            s,
            "\n# Restore priority UID filter\nif /data/adb/ksud susfs set-priority-uid-filter {quoted} >> \"$LOG_FILE\" 2>&1; then\n  echo \"$(get_current_time): Priority UID filter applied\" >> \"$LOG_FILE\"\nelse\n  echo \"$(get_current_time): Failed to apply priority UID filter\" >> \"$LOG_FILE\"\nfi"
        );
    }
    if !sus_path_uid_filter.trim().is_empty() {
        let quoted = shell_quote(sus_path_uid_filter);
        let _ = writeln!(
            s,
            "\n# Restore sus_path UID filter\nif /data/adb/ksud susfs set-sus-path-uid-filter {quoted} >> \"$LOG_FILE\" 2>&1; then\n  echo \"$(get_current_time): sus_path UID filter applied\" >> \"$LOG_FILE\"\nelse\n  echo \"$(get_current_time): Failed to apply sus_path UID filter\" >> \"$LOG_FILE\"\nfi"
        );
    }
}

/// Apply the `hide_sus_mnts_for_non_su_procs` toggle.
fn append_hide_sus_mounts_command(s: &mut String, hide_sus_mounts_for_all_procs: bool) {
    let hide_val: u32 = u32::from(hide_sus_mounts_for_all_procs);
    let desc = if hide_sus_mounts_for_all_procs {
        "对所有进程隐藏"
    } else {
        "仅对非KSU进程隐藏"
    };
    let _ = writeln!(
        s,
        "\n# 设置SUS挂载隐藏控制\nif /data/adb/ksud susfs hide-sus-mnts-for-non-su-procs {hide_val} >> \"$LOG_FILE\" 2>&1; then\n  echo \"$(get_current_time): SUS挂载隐藏控制设置为: {desc}\" >> \"$LOG_FILE\"\nelse\n  echo \"$(get_current_time): 设置SUS挂载隐藏控制失败\" >> \"$LOG_FILE\"\nfi\n"
    );
}

/// Spoof cmdline / bootconfig from the file saved by the manager.
fn append_cmdline_command(s: &mut String, cmdline_or_bootconfig_path: &str) {
    if cmdline_or_bootconfig_path.is_empty() {
        return;
    }
    let quoted = shell_quote(cmdline_or_bootconfig_path);
    let escaped = cmdline_or_bootconfig_path.replace('\'', "'\\''");
    let _ = writeln!(
        s,
        "\n# 替换 cmdline / bootconfig\nif [ -f {quoted} ]; then\n  if /data/adb/ksud susfs set-cmdline-or-bootconfig {quoted} >> \"$LOG_FILE\" 2>&1; then\n    echo \"$(get_current_time): 应用 cmdline/bootconfig 文件: {escaped}\" >> \"$LOG_FILE\"\n  else\n    echo \"$(get_current_time): 应用 cmdline/bootconfig 失败: {escaped}\" >> \"$LOG_FILE\"\n  fi\nelse\n  echo \"$(get_current_time): 指定的cmdline/bootconfig文件不存在: {escaped}\" >> \"$LOG_FILE\"\nfi"
    );
}

/// Set the SuSFS kernel log toggle.
fn append_enable_log_command(s: &mut String, enable_log: bool) {
    let log_val: u32 = u32::from(enable_log);
    let desc = if enable_log { "启用" } else { "禁用" };
    let _ = writeln!(
        s,
        "\n# 设置日志启用状态\nif /data/adb/ksud susfs enable-log {log_val} >> \"$LOG_FILE\" 2>&1; then\n  echo \"$(get_current_time): 日志功能设置为: {desc}\" >> \"$LOG_FILE\"\nelse\n  echo \"$(get_current_time): 设置日志功能失败\" >> \"$LOG_FILE\"\nfi\n"
    );
}

/// Set the SuSFS AVC log spoofing toggle.
fn append_avc_log_spoofing_command(s: &mut String, enable_avc_log_spoofing: bool) {
    let avc_val: u32 = u32::from(enable_avc_log_spoofing);
    let desc = if enable_avc_log_spoofing {
        "启用"
    } else {
        "禁用"
    };
    let _ = writeln!(
        s,
        "\n# 设置AVC日志欺骗状态\nif /data/adb/ksud susfs enable-avc-log-spoofing {avc_val} >> \"$LOG_FILE\" 2>&1; then\n  echo \"$(get_current_time): AVC日志欺骗功能设置为: {desc}\" >> \"$LOG_FILE\"\nelse\n  echo \"$(get_current_time): 设置AVC日志欺骗功能失败\" >> \"$LOG_FILE\"\nfi\n"
    );
}

/// Set the spoofed uname / build time.
fn append_uname_command(s: &mut String, uname_value: &str, build_time_value: &str) {
    let _ = writeln!(
        s,
        "# 设置uname和构建时间\nif /data/adb/ksud susfs set-uname {} {} >> \"$LOG_FILE\" 2>&1; then\n  echo \"$(get_current_time): 设置uname为: {}, 构建时间为: {}\" >> \"$LOG_FILE\"\nelse\n  echo \"$(get_current_time): 设置uname/构建时间失败\" >> \"$LOG_FILE\"\nfi\n",
        shell_quote(uname_value),
        shell_quote(build_time_value),
        uname_value.replace('\'', "'\\''"),
        build_time_value.replace('\'', "'\\''")
    );
}

/// `service.sh` — runs once the system services are up.
///
/// The post-fs-data pass already applied everything whose target existed at
/// that point. This pass adds the paths that appeared later (for example
/// credential-encrypted app data after unlock) without an unconditional
/// `/sdcard` wait or fixed sleep; `boot-completed.sh` remains the final
/// retry.
#[allow(clippy::too_many_arguments, clippy::fn_params_excessive_bools)]
pub fn service(
    sus_paths: &[String],
    sus_loop_paths: &[String],
    sus_maps: &[String],
    kstat_configs: &[String],
    add_kstat_paths: &[String],
    cmdline_or_bootconfig_path: &str,
    uname_value: &str,
    build_time_value: &str,
    execute_in_post_fs_data: bool,
    enable_log: bool,
    enable_hide_bl: bool,
    enable_cleanup_residue: bool,
    priority_uid_filter: &str,
    sus_path_uid_filter: &str,
    hide_sus_mounts_for_all_procs: bool,
) -> String {
    let mut s = String::new();

    s.push_str("#!/system/bin/sh\n");
    s.push_str("# SuSFS Service Script\n");
    s.push_str("# 在系统服务启动后立即执行；不等待 /sdcard，缺失路径交给 boot-completed 重试\n\n");
    s.push_str(&prelude::log_setup("susfs_service.log"));
    s.push('\n');
    s.push_str(&prelude::binary_check());
    s.push_str("\n# ksud存在，继续执行\n\n");

    // Set the global toggles before registering any path.
    append_enable_log_command(&mut s, enable_log);
    append_uid_filter_commands(&mut s, priority_uid_filter, sus_path_uid_filter);
    append_hide_sus_mounts_command(&mut s, hide_sus_mounts_for_all_procs);

    if should_configure_in_service(
        sus_paths,
        sus_loop_paths,
        sus_maps,
        kstat_configs,
        add_kstat_paths,
        cmdline_or_bootconfig_path,
        execute_in_post_fs_data,
        uname_value,
        build_time_value,
    ) {
        // App data on credential-encrypted storage only appears after unlock.
        // Do not block on `/sdcard` here: add whatever exists now, and let
        // the boot-completed pass retry the rest.
        append_path_commands(
            &mut s,
            "添加SUS路径",
            "add-sus-path",
            "添加SUS路径",
            sus_paths,
        );

        // uname (non-post-fs-data)
        if !execute_in_post_fs_data
            && (uname_value != DEFAULT_UNAME || build_time_value != DEFAULT_BUILD_TIME)
        {
            append_uname_command(&mut s, uname_value, build_time_value);
        }

        append_loop_path_commands(&mut s, "添加SUS循环路径", "添加SUS循环路径", sus_loop_paths);
        append_path_commands(
            &mut s,
            "添加SUS映射",
            "add-sus-map",
            "添加SUS映射",
            sus_maps,
        );

        append_path_commands(
            &mut s,
            "添加Kstat路径",
            "add-sus-kstat",
            "添加Kstat路径",
            add_kstat_paths,
        );
        append_static_kstat_commands(&mut s, kstat_configs);

        // cmdline / bootconfig spoof — only emit when the user actually
        // picked a file via the SAF picker.
        append_cmdline_command(&mut s, cmdline_or_bootconfig_path);
    }

    // hide bl
    if enable_hide_bl {
        s.push_str("\n# 隐藏BL\n");
        s.push_str(&hide_bl::hide_bl_section());
        s.push('\n');
        s.push_str(&hide_bl::hide_bl_props());
        s.push('\n');
    }

    // cleanup residue
    if enable_cleanup_residue {
        s.push_str(&cleanup::cleanup_residue_section());
    }

    let _ = writeln!(
        s,
        "echo \"$(get_current_time): Service脚本执行完成\" >> \"$LOG_FILE\""
    );

    s
}

/// `post-fs-data.sh` — runs after `/data` is mounted but before services
/// start. This is the earliest point at which ksud (and therefore SuSFS) is
/// available, so apply everything whose target already exists here. Normal
/// `add-sus-path` entries on credential-encrypted storage or `/sdcard` are
/// skipped until they exist and retried by `service.sh` /
/// `boot-completed.sh` after unlock; `add-sus-path-loop` entries need no
/// existence check, so they are registered right away.
#[allow(clippy::too_many_arguments, clippy::fn_params_excessive_bools)]
pub fn post_fs_data(
    uname_value: &str,
    build_time_value: &str,
    execute_in_post_fs_data: bool,
    enable_avc_log_spoofing: bool,
    enable_log: bool,
    hide_sus_mounts_for_all_procs: bool,
    sus_paths: &[String],
    sus_loop_paths: &[String],
    sus_maps: &[String],
    kstat_configs: &[String],
    priority_uid_filter: &str,
    sus_path_uid_filter: &str,
    cmdline_or_bootconfig_path: &str,
) -> String {
    let mut s = String::new();

    s.push_str("#!/system/bin/sh\n");
    s.push_str("# SuSFS Post-FS-Data Script\n");
    s.push_str("# 在文件系统挂载后但在系统完全启动前执行\n");
    s.push_str("# 尽早应用已存在的路径；CE/sdcard 路径在解锁后由 service/boot-completed 补齐\n\n");
    s.push_str(&prelude::log_setup("susfs_post_fs_data.log"));
    s.push('\n');
    s.push_str(&prelude::binary_check());
    s.push('\n');
    let _ = writeln!(
        s,
        "echo \"$(get_current_time): Post-FS-Data脚本开始执行\" >> \"$LOG_FILE\"\n"
    );

    if execute_in_post_fs_data
        && (uname_value != DEFAULT_UNAME || build_time_value != DEFAULT_BUILD_TIME)
    {
        append_uname_command(&mut s, uname_value, build_time_value);
    }

    append_enable_log_command(&mut s, enable_log);
    append_avc_log_spoofing_command(&mut s, enable_avc_log_spoofing);

    append_hide_sus_mounts_command(&mut s, hide_sus_mounts_for_all_procs);
    append_uid_filter_commands(&mut s, priority_uid_filter, sus_path_uid_filter);

    append_path_commands(
        &mut s,
        "添加SUS路径",
        "add-sus-path",
        "添加SUS路径",
        sus_paths,
    );
    append_loop_path_commands(&mut s, "添加SUS循环路径", "添加SUS循环路径", sus_loop_paths);
    append_path_commands(
        &mut s,
        "添加SUS映射",
        "add-sus-map",
        "添加SUS映射",
        sus_maps,
    );
    append_static_kstat_commands(&mut s, kstat_configs);
    append_cmdline_command(&mut s, cmdline_or_bootconfig_path);

    let _ = writeln!(
        s,
        "echo \"$(get_current_time): Post-FS-Data脚本执行完成\" >> \"$LOG_FILE\""
    );

    s
}

/// `post-mount.sh` — currently a stub that just records that it ran.
pub fn post_mount() -> String {
    let mut s = String::new();
    s.push_str("#!/system/bin/sh\n");
    s.push_str("# SuSFS Post-Mount Script\n");
    s.push_str("# 在所有分区挂载完成后执行\n\n");
    s.push_str(&prelude::log_setup("susfs_post_mount.log"));
    s.push('\n');
    let _ = writeln!(
        s,
        "echo \"$(get_current_time): Post-Mount脚本开始执行\" >> \"$LOG_FILE\"\n"
    );
    s.push_str(&prelude::binary_check());
    s.push('\n');
    let _ = writeln!(
        s,
        "echo \"$(get_current_time): Post-Mount脚本执行完成\" >> \"$LOG_FILE\""
    );
    s
}

/// `boot-completed.sh` — runs after the system has finished booting.
///
/// Final retry for normal sus_path entries that only appeared after unlock
/// (credential-encrypted app data, `/sdcard`, cgroup directories, ...).
/// Loop paths are repeated as a final fallback if the service pass was
/// interrupted; the kernel tolerates duplicate loop entries and re-flags
/// each one on app spawn.
#[allow(clippy::too_many_arguments)]
pub fn boot_completed(
    hide_sus_mounts_for_all_procs: bool,
    sus_paths: &[String],
    sus_loop_paths: &[String],
    sus_maps: &[String],
    kstat_configs: &[String],
    priority_uid_filter: &str,
    sus_path_uid_filter: &str,
) -> String {
    let mut s = String::new();

    s.push_str("#!/system/bin/sh\n");
    s.push_str("# SuSFS Boot-Completed Script\n");
    s.push_str("# 在系统完全启动后执行\n\n");
    s.push_str(&prelude::log_setup("susfs_boot_completed.log"));
    s.push('\n');
    let _ = writeln!(
        s,
        "echo \"$(get_current_time): Boot-Completed脚本开始执行\" >> \"$LOG_FILE\"\n"
    );
    s.push_str(&prelude::binary_check());
    s.push('\n');

    append_hide_sus_mounts_command(&mut s, hide_sus_mounts_for_all_procs);
    append_uid_filter_commands(&mut s, priority_uid_filter, sus_path_uid_filter);

    append_path_commands(
        &mut s,
        "添加SUS路径",
        "add-sus-path",
        "添加SUS路径",
        sus_paths,
    );
    append_loop_path_commands(&mut s, "添加SUS循环路径", "添加SUS循环路径", sus_loop_paths);
    append_path_commands(
        &mut s,
        "添加SUS映射",
        "add-sus-map",
        "添加SUS映射",
        sus_maps,
    );
    append_static_kstat_commands(&mut s, kstat_configs);

    let _ = writeln!(
        s,
        "echo \"$(get_current_time): Boot-Completed脚本执行完成\" >> \"$LOG_FILE\""
    );

    s
}
