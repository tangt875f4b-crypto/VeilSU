package me.veilsu.manager.data.repository

import me.veilsu.manager.ui.util.KpmRunResult
import me.veilsu.manager.ui.viewmodel.KpmViewModel

interface KpmRepository {
    suspend fun getModuleList(): Result<List<KpmViewModel.ModuleInfo>>
    suspend fun getModuleInfo(moduleId: String): Result<String>
    suspend fun loadModule(path: String, args: String? = null): Result<Unit>
    suspend fun unloadModule(moduleId: String): Result<Unit>
    suspend fun controlModule(moduleId: String, args: String? = null): Result<Int>
    suspend fun getModuleCommands(moduleId: String): Result<String>
    suspend fun getModuleHelp(moduleId: String): Result<String>
    suspend fun runModule(moduleId: String, args: String): Result<KpmRunResult>
    suspend fun readKernelLog(moduleId: String): List<String>
}
