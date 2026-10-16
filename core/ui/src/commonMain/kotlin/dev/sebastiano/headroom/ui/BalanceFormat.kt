package dev.sebastiano.headroom.ui

import dev.sebastiano.headroom.model.QuotaBalance

expect fun formatBalance(balance: QuotaBalance, locale: String): String

expect fun formatAmount(amount: Double, locale: String): String

expect fun formatMoney(amount: Double, unit: String, locale: String): String

expect fun formatTenths(amount: Double, locale: String): String
