#![allow(clippy::missing_errors_doc, clippy::must_use_candidate)]

//! Built-in Zygisk — REMOVED in VeilSU v1 (minimal scope: modules + su only,
//! no Zygisk at all).
//!
//! This file used to be the full built-in NyaZygisk implementation
//! (`external/BuiltZygisk` submodule, ~1,800 lines: zygiskd, ptrace tracer,
//! daemon/companion, zygote injection). VeilSU v1 ships no zygote injection:
//! no `libzygisk.so`, no zygiskd/zygisk-ptrace processes, no
//! `/data/system/nyazygisk` socket, no `/data/adb/ksu/.builtin_zygisk` flag.
//!
//! The stub keeps the exact public API the rest of ksud was written against
//! so `init_event`, `late_load`, `soft_reboot`, `module` and `cli` compile
//! unchanged:
//! * Boot-path calls (`apply_sepolicy`, `bootstrap`, `ensure_monitor`,
//!   `ensure_armed`, `rearm`, `cleanup_disabled`, `shutdown`, `disable`)
//!   are no-ops that return `Ok(())` — boot and module loading never depend
//!   on Zygisk.
//! * Status queries all report "not present": `is_enabled` is false, no
//!   monitor/daemon processes, no injected modules, no providers.
//! * Explicitly turning it on (`enable`, `switch_to`, `config_set`) is an
//!   error — there is nothing to turn on.
//!
//! A module that ships a `zygisk/` directory is treated as a plain module:
//! its `system/` files still mount, its zygote code simply never runs.
//! If Zygisk ever comes back, restore the original `builtin_zygisk.rs` from
//! upstream SukiSU Nova and re-add the `external/BuiltZygisk` submodule.

use anyhow::{Result, bail};
use std::path::Path;

/// Target id the app used for the built-in implementation.
pub const BUILTIN_TARGET: &str = "builtin";

/// Monitor and daemon processes of one implementation, for the dashboard's process rows.
pub struct ImplementationProcesses {
    pub monitors: Vec<(i32, String)>,
    pub daemons: Vec<(i32, String)>,
}

/// One Zygisk module the built-in is serving, as both ABI daemons and the monitor see it.
pub struct InjectedModule {
    pub id: String,
    pub name: String,
    pub version: String,
    /// `zygisk` for the Magisk API, `next` for the Zygisk Next API.
    pub kind: String,
    /// ABIs whose daemon loaded the module, `32` and/or `64`.
    pub abi: Vec<String>,
    /// Injection targets declared by a Zygisk Next module, as plain names or paths.
    pub targets: Vec<String>,
    pub companion: bool,
    /// `injected`, `loaded`, `failed` or `unknown`.
    pub state: String,
    /// `(name, pid, kind)` with kind `companion` or `target`.
    pub processes: Vec<(String, i32, String)>,
}

/// The app used to derive this from `module list`, a command that also serves the module
/// screen and carries flags for both, so the two kept drifting apart. The answer is
/// computed here instead and read with a single `ksud zygisk info`.
pub struct ImplementationInfo {
    /// `running`, `reboot`, `disabled` or `none` for the app to localize.
    pub state: &'static str,
    /// [`BUILTIN_TARGET`] or the provider module's id, for `zygisk switch`.
    pub id: String,
    pub name: String,
    pub version: String,
}

pub fn is_enabled() -> bool {
    false
}

pub fn enable() -> Result<()> {
    bail!("VeilSU v1: built-in Zygisk was removed (minimal scope: modules + su only)")
}

pub fn disable() -> Result<()> {
    Ok(())
}

pub fn cleanup_disabled() -> Result<()> {
    Ok(())
}

pub fn monitor_process() -> Option<(i32, String)> {
    None
}

pub fn daemon_processes() -> Vec<(i32, String)> {
    Vec::new()
}

pub fn is_monitor_running() -> bool {
    false
}

pub fn is_daemon_running() -> bool {
    false
}

pub fn processes(_target: &str) -> Result<ImplementationProcesses> {
    Ok(ImplementationProcesses {
        monitors: Vec::new(),
        daemons: Vec::new(),
    })
}

pub fn injected_modules() -> Vec<InjectedModule> {
    Vec::new()
}

pub fn is_monitor_active() -> bool {
    false
}

pub fn is_daemon_active() -> bool {
    false
}

pub fn is_zygote_injected() -> bool {
    false
}

pub fn is_fully_active() -> bool {
    false
}

pub fn zygote_states() -> Vec<(String, String)> {
    Vec::new()
}

pub fn provider_ids() -> Vec<String> {
    Vec::new()
}

pub fn suppresses_module_dir(_path: &Path) -> bool {
    false
}

pub fn short_version(version: &str) -> String {
    // `module.prop` versions usually carry build details in parentheses, e.g.
    // `v1.2.0 (1234-abc1234-release)`. Only the leading version is display material.
    let end = version.find('(').unwrap_or(version.len());
    version[..end].trim_end().to_owned()
}

pub fn implementation_info() -> ImplementationInfo {
    ImplementationInfo {
        state: "disabled",
        id: String::new(),
        name: String::new(),
        version: String::new(),
    }
}

/// Whether the id names a Zygisk implementation, embedded or a standalone provider.
pub fn is_provider_id(_module_id: &str) -> bool {
    false
}

pub fn is_provider_module(_dir_id: &str, _prop_id: &str) -> bool {
    false
}

pub fn is_provider_running(_module_id: &str) -> bool {
    false
}

pub fn switch_to(_target: &str) -> Result<()> {
    bail!("VeilSU v1: built-in Zygisk was removed (minimal scope: modules + su only)")
}

pub fn version() -> Option<(String, String)> {
    None
}

pub fn apply_sepolicy() -> Result<()> {
    Ok(())
}

pub fn bootstrap() -> Result<()> {
    Ok(())
}

pub fn ensure_monitor() -> Result<()> {
    Ok(())
}

pub fn rearm() -> Result<()> {
    Ok(())
}

pub fn ensure_armed() -> Result<()> {
    Ok(())
}

pub fn shutdown() -> Result<()> {
    Ok(())
}

pub fn config_get_all() -> Vec<(&'static str, String)> {
    Vec::new()
}

pub fn config_set(_key: &str, _value: &str) -> Result<()> {
    bail!("VeilSU v1: built-in Zygisk was removed (minimal scope: modules + su only)")
}
