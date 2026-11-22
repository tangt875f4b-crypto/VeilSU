package me.veilsu.manager.data.repository

import me.veilsu.manager.data.model.Module
import me.veilsu.manager.data.model.ModuleUpdateInfo

interface ModuleRepository {
    suspend fun getModules(): Result<List<Module>>
    suspend fun checkUpdate(module: Module): Result<ModuleUpdateInfo>
}
