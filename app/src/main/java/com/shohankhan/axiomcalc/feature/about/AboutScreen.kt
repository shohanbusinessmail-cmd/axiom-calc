package com.shohankhan.axiomcalc.feature.about

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.shohankhan.axiomcalc.BuildConfig
import com.shohankhan.axiomcalc.R
import com.shohankhan.axiomcalc.core.design.AxiomDimens
import com.shohankhan.axiomcalc.core.ui.SectionCard
import kotlinx.coroutines.launch

/**
 * About screen.
 *
 * Contains only facts that are true of this repository: the owner's name and
 * email, the version from the build config, the offline/no-tracking statement
 * and where the source documentation lives. There is no invented company,
 * website, social link, privacy-policy URL or legal claim — the email address is
 * the contact channel, opened through a standard `mailto` intent.
 */
@Composable
fun AboutScreen(
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AxiomDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(
                    R.string.about_version,
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        SectionCard(title = stringResource(R.string.about_developed_by)) {
            Text(stringResource(R.string.about_owner_name), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.about_email_label),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_mail),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                TextButton(onClick = {
                    val sent = sendOwnerEmail(context) { reason ->
                        scope.launch { snackbarHostState.showSnackbar(reason) }
                    }
                    if (!sent) {
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.about_no_mail_client))
                        }
                    }
                }) {
                    Text(stringResource(R.string.about_owner_email))
                }
            }
        }

        SectionCard(title = stringResource(R.string.about_offline_title)) {
            Text(
                text = stringResource(R.string.about_offline_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = stringResource(R.string.about_documentation_title)) {
            Text(
                text = stringResource(R.string.about_documentation_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Opens the owner's address in the user's mail app.
 *
 * Uses [Intent.ACTION_SENDTO] with a `mailto` URI so only mail clients are
 * offered, and reports failure through [onError] instead of crashing or
 * silently doing nothing. Returns true when a mail client was launched.
 */
private fun sendOwnerEmail(context: android.content.Context, onError: (String) -> Unit): Boolean = try {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:" + context.getString(R.string.about_owner_email))
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.app_name))
    }
    context.startActivity(intent)
    true
} catch (e: ActivityNotFoundException) {
    onError(context.getString(R.string.about_no_mail_client))
    false
} catch (e: SecurityException) {
    onError(context.getString(R.string.about_no_mail_client))
    false
}
