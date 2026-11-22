package it.labirintodigitale.ricette.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import it.labirintodigitale.ricette.R
import it.labirintodigitale.ricette.data.Settings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, firstRun: Boolean, onDone: () -> Unit, onBack: (() -> Unit)?) {
    val current = remember { vm.repo.settings }
    var url by rememberSaveable { mutableStateOf(current.url) }
    var altUrl by rememberSaveable { mutableStateOf(current.altUrl) }
    // Username and password unless a long-lived token is already in use.
    var useToken by rememberSaveable { mutableStateOf(current.token.isNotBlank()) }
    var username by rememberSaveable { mutableStateOf(current.username) }
    var password by remember { mutableStateOf("") } // never saved, not even in the instance state
    var token by rememberSaveable { mutableStateOf(current.token) }
    var showSecret by rememberSaveable { mutableStateOf(false) }
    // Two-factor step: non-null while Home Assistant waits for the code.
    var mfaModule by rememberSaveable { mutableStateOf<String?>(null) }
    var askCode by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val state by vm.state.collectAsState()

    fun handle(outcome: MainViewModel.LoginOutcome) {
        when (outcome) {
            MainViewModel.LoginOutcome.Done -> {
                password = ""
                askCode = false
                onDone()
            }
            is MainViewModel.LoginOutcome.NeedsCode -> {
                askCode = true
                mfaModule = outcome.module
                code = ""
            }
            is MainViewModel.LoginOutcome.Error -> error = outcome.message
        }
    }

    fun submit() {
        busy = true
        error = null
        scope.launch {
            when {
                useToken -> {
                    val err = vm.saveToken(Settings(url, altUrl, token = token))
                    if (err == null) onDone() else error = err
                }
                askCode -> handle(vm.loginCode(code))
                else -> handle(vm.login(url, altUrl, username, password))
            }
            busy = false
        }
    }

    val canSubmit = !busy && url.isNotBlank() && when {
        useToken -> token.isNotBlank()
        askCode -> code.isNotBlank()
        else -> username.isNotBlank() && password.isNotEmpty()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (firstRun) R.string.welcome else R.string.settings)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.settings_intro), style = MaterialTheme.typography.bodyMedium)
            if (!firstRun && current.configured) {
                Text(
                    if (current.token.isNotBlank()) stringResource(R.string.connected_token)
                    else stringResource(R.string.connected_as, current.username),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            OutlinedTextField(
                url, { url = it; askCode = false },
                label = { Text(stringResource(R.string.ha_url)) },
                placeholder = { Text("http://homeassistant.local:8123") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                altUrl, { altUrl = it; askCode = false },
                label = { Text(stringResource(R.string.ha_alt_url)) },
                placeholder = { Text("https://xxxx.ui.nabu.casa") },
                supportingText = { Text(stringResource(R.string.ha_alt_url_help)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !useToken,
                    onClick = { useToken = false; error = null },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text(stringResource(R.string.auth_password)) }
                SegmentedButton(
                    selected = useToken,
                    onClick = { useToken = true; askCode = false; error = null },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text(stringResource(R.string.auth_token)) }
            }

            val secretToggle: @Composable () -> Unit = {
                TextButton(onClick = { showSecret = !showSecret }) {
                    Text(stringResource(if (showSecret) R.string.hide else R.string.show))
                }
            }
            val secretTransform = if (showSecret) VisualTransformation.None else PasswordVisualTransformation()

            when {
                useToken -> OutlinedTextField(
                    token, { token = it },
                    label = { Text(stringResource(R.string.token)) },
                    supportingText = { Text(stringResource(R.string.token_help)) },
                    singleLine = true,
                    visualTransformation = secretTransform,
                    trailingIcon = secretToggle,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                askCode -> OutlinedTextField(
                    code, { code = it.filter(Char::isLetterOrDigit).take(12) },
                    label = { Text(stringResource(R.string.mfa_code)) },
                    supportingText = {
                        Text(stringResource(R.string.mfa_help, mfaModule ?: stringResource(R.string.mfa_app)))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                else -> {
                    OutlinedTextField(
                        username, { username = it },
                        label = { Text(stringResource(R.string.username)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        password, { password = it },
                        label = { Text(stringResource(R.string.password)) },
                        supportingText = { Text(stringResource(R.string.password_help)) },
                        singleLine = true,
                        visualTransformation = secretTransform,
                        trailingIcon = secretToggle,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(enabled = canSubmit, onClick = ::submit) {
                    Text(stringResource(if (useToken) R.string.connect_save else R.string.login))
                }
                if (askCode && !busy) {
                    TextButton(onClick = { askCode = false; error = null }) { Text(stringResource(R.string.cancel)) }
                }
                if (busy) {
                    Spacer(Modifier.size(16.dp))
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
            if (!firstRun) {
                Text(stringResource(R.string.local_copy), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.local_copy_info, state.favorites.size) + "\n" + lastSyncText(state.lastSync),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
