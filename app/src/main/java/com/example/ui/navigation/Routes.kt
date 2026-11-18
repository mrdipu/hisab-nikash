package com.example.ui.navigation

import kotlinx.serialization.Serializable

sealed interface AppRoute {
    @Serializable data object Home : AppRoute
    @Serializable data object Transactions : AppRoute
    @Serializable data object Budget : AppRoute
    @Serializable data object Analytics : AppRoute
    @Serializable data object Settings : AppRoute

    @Serializable data object Onboarding : AppRoute
    @Serializable data class AddEditEntry(
        val initialTab: String = "EXPENSE",
        val existingId: String? = null
    ) : AppRoute

    @Serializable data object Accounts : AppRoute
    @Serializable data object Categories : AppRoute
    @Serializable data object SavingsGoals : AppRoute
    @Serializable data object Recurring : AppRoute
    @Serializable data object Reports : AppRoute
    @Serializable data object ManualBackup : AppRoute
    @Serializable data object DriveBackup : AppRoute
    @Serializable data object Security : AppRoute
    @Serializable data object About : AppRoute
    @Serializable data object AlertsHistory : AppRoute
}
