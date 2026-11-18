package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LaptopMac
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.AccountType
import com.example.data.local.DateFormatStyle
import com.example.data.local.GoalPreset
import com.example.domain.BudgetPeriodCalculator
import com.example.domain.MoneyFormatter
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun OfficialLogoImage(
    modifier: Modifier = Modifier,
    size: Dp = 88.dp
) {
    Surface(
        modifier = modifier
            .size(size)
            .aspectRatio(1f),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp
    ) {
        Image(
            painter = painterResource(id = R.drawable.dz_official_logo),
            contentDescription = stringResource(R.string.app_name),
            contentScale = ContentScale.Fit,
            modifier = Modifier.padding(6.dp)
        )
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (action != null) {
                Spacer(modifier = Modifier.height(16.dp))
                action()
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        if (actionLabel != null && onAction != null) {
            androidx.compose.material3.TextButton(onClick = onAction) {
                Text(text = actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

fun categoryIconForKey(key: String): ImageVector = when (key) {
    "payments" -> Icons.Filled.Payments
    "storefront" -> Icons.Filled.Storefront
    "laptop_mac" -> Icons.Filled.LaptopMac
    "card_giftcard" -> Icons.Filled.CardGiftcard
    "redeem" -> Icons.Filled.Redeem
    "trending_up" -> Icons.AutoMirrored.Filled.TrendingUp
    "apartment" -> Icons.Filled.Apartment
    "restaurant" -> Icons.Filled.Restaurant
    "shopping_basket" -> Icons.Filled.ShoppingBasket
    "shopping_bag" -> Icons.Filled.ShoppingBag
    "directions_bus" -> Icons.Filled.DirectionsBus
    "home" -> Icons.Filled.Home
    "bolt" -> Icons.Filled.Bolt
    "local_fire_department" -> Icons.Filled.LocalFireDepartment
    "water_drop" -> Icons.Filled.WaterDrop
    "wifi" -> Icons.Filled.Wifi
    "smartphone" -> Icons.Filled.Smartphone
    "school" -> Icons.Filled.School
    "medical_services" -> Icons.Filled.MedicalServices
    "movie" -> Icons.Filled.Movie
    "flight" -> Icons.Filled.Flight
    "family_restroom" -> Icons.Filled.FamilyRestroom
    "spa" -> Icons.Filled.Spa
    "transfer" -> Icons.Filled.SwapHoriz
    "adjust" -> Icons.Filled.Tune
    else -> Icons.Filled.Category
}

fun accountIconForType(type: AccountType): ImageVector = when (type) {
    AccountType.CASH -> Icons.Filled.Payments
    AccountType.BANK -> Icons.Filled.AccountBalance
    AccountType.BKASH, AccountType.NAGAD, AccountType.ROCKET -> Icons.Filled.PhoneAndroid
    AccountType.SAVINGS -> Icons.Filled.Savings
    AccountType.OTHER -> Icons.Filled.AccountBalanceWallet
}

@Composable
fun accountTypeLabel(type: AccountType): String = when (type) {
    AccountType.CASH -> stringResource(R.string.type_cash)
    AccountType.BANK -> stringResource(R.string.type_bank)
    AccountType.BKASH -> stringResource(R.string.type_bkash)
    AccountType.NAGAD -> stringResource(R.string.type_nagad)
    AccountType.ROCKET -> stringResource(R.string.type_rocket)
    AccountType.SAVINGS -> stringResource(R.string.type_savings)
    AccountType.OTHER -> stringResource(R.string.type_other)
}

fun goalIconForPreset(preset: GoalPreset): ImageVector = when (preset) {
    GoalPreset.EMERGENCY_FUND -> Icons.Filled.MedicalServices
    GoalPreset.PHONE -> Icons.Filled.Smartphone
    GoalPreset.LAPTOP -> Icons.Filled.LaptopMac
    GoalPreset.EDUCATION -> Icons.Filled.School
    GoalPreset.TRAVEL -> Icons.Filled.Flight
    GoalPreset.HOME -> Icons.Filled.Home
    GoalPreset.CUSTOM -> Icons.Filled.Savings
}

fun formatUtcDate(
    timestampUtc: Long,
    style: DateFormatStyle,
    isBangla: Boolean
): String {
    val pattern = when (style) {
        DateFormatStyle.DD_MM_YYYY -> "dd/MM/yyyy"
        DateFormatStyle.YYYY_MM_DD -> "yyyy-MM-dd"
        DateFormatStyle.DD_MMM_YYYY -> "dd MMM yyyy"
    }
    val locale = if (isBangla) Locale.forLanguageTag("bn-BD") else Locale.ENGLISH
    val formatter = DateTimeFormatter.ofPattern(pattern, locale).withZone(BudgetPeriodCalculator.DHAKA_ZONE)
    val formatted = formatter.format(Instant.ofEpochMilli(timestampUtc))
    return MoneyFormatter.localizeDigits(formatted, isBangla)
}

@Composable
fun BadgeChip(
    label: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = containerColor
    ) {
        Text(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
