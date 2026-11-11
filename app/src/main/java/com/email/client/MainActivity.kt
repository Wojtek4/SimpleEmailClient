package com.email.client

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.email.client.data.local.dao.AccountDao
import com.email.client.presentation.compose.ComposeScreen
import com.email.client.presentation.compose.ComposeViewModel
import com.email.client.presentation.detail.MessageDetailScreen
import com.email.client.presentation.detail.MessageDetailViewModel
import com.email.client.presentation.mailbox.MailboxScreen
import com.email.client.presentation.mailbox.MailboxViewModel
import com.email.client.presentation.onboarding.OnboardingScreen
import com.email.client.presentation.onboarding.OnboardingViewModel
import com.email.client.presentation.theme.SimpleEmailTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var accountDao: AccountDao

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            SimpleEmailTheme {
                // Request Notification Permission on Android 13+ (API 33+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val permissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { _ -> }

                    LaunchedEffect(Unit) {
                        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                AppRoot(
                    accountDao = accountDao,
                    initialIntent = intent
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    companion object {
        const val EXTRA_MESSAGE_ID = "extra_message_id"
    }
}

@Composable
fun AppRoot(
    accountDao: AccountDao,
    initialIntent: Intent?
) {
    val accounts by accountDao.getAllAccounts().collectAsState(initial = null)
    val navController = rememberNavController()

    if (accounts == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    // Direct deep-link navigation if opened from email notification
    LaunchedEffect(initialIntent) {
        val targetMessageId = initialIntent?.getLongExtra(MainActivity.EXTRA_MESSAGE_ID, -1L) ?: -1L
        if (targetMessageId > 0 && accounts!!.isNotEmpty()) {
            navController.navigate("message_detail/$targetMessageId")
        }
    }

    val startDestination = if (accounts!!.isEmpty()) "onboarding" else "mailbox"

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("onboarding") {
            val viewModel = hiltViewModel<OnboardingViewModel>()
            OnboardingScreen(
                viewModel = viewModel,
                onAccountAdded = {
                    navController.navigate("mailbox") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            )
        }

        composable("mailbox") {
            val viewModel = hiltViewModel<MailboxViewModel>()
            MailboxScreen(
                viewModel = viewModel,
                onNavigateToMessage = { messageId ->
                    navController.navigate("message_detail/$messageId")
                },
                onNavigateToCompose = {
                    navController.navigate("compose")
                },
                onNavigateToAddAccount = {
                    navController.navigate("onboarding")
                }
            )
        }

        composable(
            route = "message_detail/{messageId}",
            arguments = listOf(
                navArgument("messageId") { type = NavType.LongType }
            )
        ) {
            val viewModel = hiltViewModel<MessageDetailViewModel>()
            MessageDetailScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onReply = { replyId ->
                    navController.navigate("compose?replyToMessageId=$replyId")
                }
            )
        }

        composable(
            route = "compose?replyToMessageId={replyToMessageId}",
            arguments = listOf(
                navArgument("replyToMessageId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) {
            val viewModel = hiltViewModel<ComposeViewModel>()
            ComposeScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
