//! SuSFS path / map / redirect operations.

use crate::susfs::abi::consts::{
    CMD_SUSFS_ADD_OPEN_REDIRECT, CMD_SUSFS_ADD_SUS_MAP, CMD_SUSFS_ADD_SUS_PATH,
    CMD_SUSFS_ADD_SUS_PATH_LOOP, ERR_CMD_NOT_SUPPORTED, SUSFS_MAX_PATHNAME,
};
use crate::susfs::abi::{SusfsMap, SusfsOpenRedirect, SusfsSusPath, send};
use crate::susfs::util::{canonicalize, copy_path_into};

/// Mark `path` as a SuSFS hidden path.
///
/// Mirrors the upstream `ksu_susfs add_sus_path` CLI, which resolves the
/// argument with `realpath(3)` before sending it to the kernel.
pub fn add_sus_path(path: &str) -> anyhow::Result<()> {
    let resolved = canonicalize(path)?;
    let resolved = resolved
        .to_str()
        .ok_or_else(|| anyhow::anyhow!("resolved path is not valid utf-8"))?;
    let mut cmd = SusfsSusPath {
        target_pathname: [0; SUSFS_MAX_PATHNAME],
        err: ERR_CMD_NOT_SUPPORTED,
    };
    copy_path_into(&mut cmd.target_pathname, resolved)?;
    send(CMD_SUSFS_ADD_SUS_PATH, &mut cmd, "add_sus_path")
}

/// Register `path` for deferred `sus_path` flagging.
///
/// The path is neither resolved nor required to exist at registration time;
/// the kernel re-resolves it and flags the matching inode whenever an app
/// process is spawned and marked umounted. This is the variant used for
/// FUSE app-data paths such as `/sdcard/Android/data/<pkg>`.
pub fn add_sus_path_loop(path: &str) -> anyhow::Result<()> {
    let mut cmd = SusfsSusPath {
        target_pathname: [0; SUSFS_MAX_PATHNAME],
        err: ERR_CMD_NOT_SUPPORTED,
    };
    copy_path_into(&mut cmd.target_pathname, path)?;
    send(CMD_SUSFS_ADD_SUS_PATH_LOOP, &mut cmd, "add_sus_path_loop")
}

/// Add an `open()`-time redirect: `target` paths will be silently opened
/// as `redirected` for processes selected by `uid_scheme`.
///
/// The upstream CLI resolves both paths with `realpath(3)` and accepts only
/// the five documented schemes (`0..=4`).
pub fn add_open_redirect(target: &str, redirected: &str, uid_scheme: u32) -> anyhow::Result<()> {
    if uid_scheme > 4 {
        anyhow::bail!("invalid uid_scheme {uid_scheme}, expected 0..=4");
    }
    let target = canonicalize(target)?;
    let redirected = canonicalize(redirected)?;
    let target = target
        .to_str()
        .ok_or_else(|| anyhow::anyhow!("resolved target path is not valid utf-8"))?;
    let redirected = redirected
        .to_str()
        .ok_or_else(|| anyhow::anyhow!("resolved redirected path is not valid utf-8"))?;
    let mut cmd = SusfsOpenRedirect {
        target_pathname: [0; SUSFS_MAX_PATHNAME],
        redirected_pathname: [0; SUSFS_MAX_PATHNAME],
        uid_scheme: uid_scheme as i32,
        err: ERR_CMD_NOT_SUPPORTED,
    };
    copy_path_into(&mut cmd.target_pathname, target)?;
    copy_path_into(&mut cmd.redirected_pathname, redirected)?;
    send(CMD_SUSFS_ADD_OPEN_REDIRECT, &mut cmd, "add_open_redirect")
}

/// Add `path` to the SuSFS map of overlay / mount roots.
pub fn add_sus_map(path: &str) -> anyhow::Result<()> {
    let mut cmd = SusfsMap {
        target_pathname: [0; SUSFS_MAX_PATHNAME],
        err: ERR_CMD_NOT_SUPPORTED,
    };
    copy_path_into(&mut cmd.target_pathname, path)?;
    send(CMD_SUSFS_ADD_SUS_MAP, &mut cmd, "add_sus_map")
}
