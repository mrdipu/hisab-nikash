package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.AccountType
import com.example.data.local.AppLanguageMode
import com.example.domain.MoneyParseResult
import com.example.domain.MoneyParser
import com.example.ui.components.OfficialLogoImage
import com.example.ui.components.accountTypeLabel
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    currentLanguage: AppLanguageMode,
    onLanguageSelected: (AppLanguageMode) -> Unit,
    onComplete: (
        language: AppLanguageMode,
        accountName: String?,
        accountType: AccountType,
        openingBalancePoisha: Long,
        optionalBudgetPoisha: Long?
    ) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    var selectedLanguage by remember(currentLanguage) { mutableStateOf(currentLanguage) }
    var accountName by remember { mutableStateOf("Cash Wallet") }
    var selectedAccountType by remember { mutableStateOf(AccountType.CASH) }
    var openingBalanceText by remember { mutableStateOf("0") }
    var budgetText by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val invalidAmountErr = stringResource(R.string.err_invalid_amount)

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(
                    onClick = {
                        onComplete(selectedLanguage, accountName.ifBlank { null }, selectedAccountType, 0L, null)
                    },
                    modifier = Modifier.testTag("onboarding_skip_btn")
                ) {
                    Text(stringResource(R.string.skip))
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 600.dp)
            ) { page ->
                when (page) {
                    0 -> OnboardingPageContent(
                        icon = Icons.Filled.AccountBalanceWallet,
                        title = stringResource(R.string.onboarding_title_1),
                        description = stringResource(R.string.onboarding_desc_1),
                        showLogo = true
                    )
                    1 -> OnboardingPageContent(
                        icon = Icons.Filled.BarChart,
                        title = stringResource(R.string.onboarding_title_2),
                        description = stringResource(R.string.onboarding_desc_2),
                        showLogo = false
                    )
                    else -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        OnboardingPageContent(
                            icon = Icons.Filled.Lock,
                            title = stringResource(R.string.onboarding_title_3),
                            description = stringResource(R.string.onboarding_desc_3),
                            showLogo = false,
                            compact = true
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.onboarding_setup_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(R.string.onboarding_language_label),
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = selectedLanguage == AppLanguageMode.ENGLISH,
                                        onClick = {
                                            selectedLanguage = AppLanguageMode.ENGLISH
                                            onLanguageSelected(AppLanguageMode.ENGLISH)
                                        },
                                        label = { Text(stringResource(R.string.lang_english)) }
                                    )
                                    FilterChip(
                                        selected = selectedLanguage == AppLanguageMode.BANGLA,
                                        onClick = {
                                            selectedLanguage = AppLanguageMode.BANGLA
                                            onLanguageSelected(AppLanguageMode.BANGLA)
                                        },
                                        label = { Text(stringResource(R.string.lang_bangla)) }
                                    )
                                }

                                OutlinedTextField(
                                    value = accountName,
                                    onValueChange = { accountName = it },
                                    label = { Text(stringResource(R.string.onboarding_account_name)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(AccountType.CASH, AccountType.BANK, AccountType.BKASH, AccountType.NAGAD).forEach { type ->
                                        FilterChip(
                                            selected = selectedAccountType == type,
                                            onClick = { selectedAccountType = type },
                                            label = { Text(accountTypeLabel(type), maxLines = 1) }
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = openingBalanceText,
                                    onValueChange = {
                                        openingBalanceText = it
                                        errorMsg = null
                                    },
                                    label = { Text(stringResource(R.string.onboarding_opening_balance)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = budgetText,
                                    onValueChange = {
                                        budgetText = it
                                        errorMsg = null
                                    },
                                    label = { Text(stringResource(R.string.onboarding_first_budget)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                errorMsg?.let {
                                    Text(
                                        text = it,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { idx ->
                    val selected = pagerState.currentPage == idx
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(if (selected) 24.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (pagerState.currentPage < 2) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    } else {
                        val openRes = MoneyParser.parseBdtToPoisha(
                            openingBalanceText.ifBlank { "0" },
                            allowZero = true
                        )
                        if (openRes !is MoneyParseResult.Valid) {
                            errorMsg = invalidAmountErr
                            return@Button
                        }
                        val budgetPoisha = if (budgetText.isBlank()) {
                            null
                        } else {
                            val bRes = MoneyParser.parseBdtToPoisha(budgetText, allowZero = false)
                            if (bRes !is MoneyParseResult.Valid) {
                                errorMsg = invalidAmountErr
                                return@Button
                            }
                            bRes.poisha
                        }
                        onComplete(
                            selectedLanguage,
                            accountName.ifBlank { null },
                            selectedAccountType,
                            openRes.poisha,
                            budgetPoisha
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .height(52.dp)
                    .testTag("onboarding_primary_btn")
            ) {
                Text(
                    text = if (pagerState.currentPage < 2) stringResource(R.string.next)
                    else stringResource(R.string.get_started)
                )
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(
    icon: ImageVector,
    title: String,
    description: String,
    showLogo: Boolean,
    compact: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (compact) 8.dp else 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (showLogo) {
            OfficialLogoImage(size = 112.dp)
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.tagline),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.powered_by),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(18.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(if (compact) 56.dp else 80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(if (compact) 28.dp else 40.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
