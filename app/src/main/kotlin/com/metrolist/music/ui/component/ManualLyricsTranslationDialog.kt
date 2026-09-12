/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.metrolist.music.LocalDatabase
import com.metrolist.music.R
import com.metrolist.music.constants.LanguageCodeToName
import com.metrolist.music.lyrics.LyricsTranslationFormat
import com.metrolist.music.lyrics.LyricsTranslationHelper
import kotlinx.coroutines.launch

@Composable
fun ManualLyricsTranslationDialog(
    sourceSnapshot: LyricsTranslationHelper.ManualLyricsSource,
    initialLanguageCode: String,
    initialMode: String,
    onDismiss: () -> Unit,
    onImportSuccess: (targetLanguageCode: String, mode: String) -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()

    var selectedLanguageCode by rememberSaveable { mutableStateOf(initialLanguageCode) }
    var selectedMode by rememberSaveable { mutableStateOf(initialMode) }

    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
    var showModeDialog by rememberSaveable { mutableStateOf(false) }

    var responseText by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var isImporting by remember { mutableStateOf(false) }
    var importErrorMessage by remember { mutableStateOf<String?>(null) }

    val targetLanguageName = LanguageCodeToName[selectedLanguageCode] ?: selectedLanguageCode

    val generatedPrompt = remember(sourceSnapshot.sourceLines, targetLanguageName, selectedMode) {
        LyricsTranslationFormat.buildManualPrompt(
            lines = sourceSnapshot.sourceLines,
            targetLanguageName = targetLanguageName,
            mode = selectedMode,
        )
    }

    val validationResult = remember(responseText.text, sourceSnapshot.sourceLines.size) {
        LyricsTranslationFormat.validateAndParseResponse(
            raw = responseText.text,
            expectedLineCount = sourceSnapshot.sourceLines.size,
        )
    }

    if (showLanguageDialog) {
        EnumDialog(
            onDismiss = { showLanguageDialog = false },
            onSelect = {
                selectedLanguageCode = it
                showLanguageDialog = false
            },
            title = stringResource(R.string.ai_target_language),
            current = selectedLanguageCode,
            values = LanguageCodeToName.keys.sortedBy { LanguageCodeToName[it] },
            valueText = { LanguageCodeToName[it] ?: it },
        )
    }

    if (showModeDialog) {
        EnumDialog(
            onDismiss = { showModeDialog = false },
            onSelect = {
                selectedMode = it
                showModeDialog = false
            },
            title = stringResource(R.string.ai_translation_mode),
            current = selectedMode,
            values = listOf("Literal", "Transcribed"),
            valueText = {
                when (it) {
                    "Literal" -> stringResource(R.string.ai_translation_literal)
                    "Transcribed" -> stringResource(R.string.ai_translation_transcribed)
                    else -> it
                }
            },
        )
    }

    DefaultDialog(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        onDismiss = onDismiss,
        icon = {
            Icon(
                painter = painterResource(R.drawable.translate),
                contentDescription = null,
            )
        },
        title = { Text(stringResource(R.string.manual_ai_translation)) },
        buttons = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }

            Spacer(Modifier.width(8.dp))

            TextButton(
                enabled = validationResult is LyricsTranslationFormat.ValidationResult.Success && !isImporting,
                onClick = {
                    val lines = (validationResult as? LyricsTranslationFormat.ValidationResult.Success)?.lines ?: return@TextButton
                    isImporting = true
                    importErrorMessage = null
                    scope.launch {
                        val result = LyricsTranslationHelper.saveAndApplyManualTranslation(
                            database = database,
                            sourceSnapshot = sourceSnapshot,
                            translatedLines = lines,
                            targetLanguageCode = selectedLanguageCode,
                            mode = selectedMode,
                        )
                        isImporting = false
                        when (result) {
                            is LyricsTranslationHelper.ManualImportResult.Success -> {
                                Toast.makeText(context, R.string.translation_imported, Toast.LENGTH_SHORT).show()
                                onImportSuccess(selectedLanguageCode, selectedMode)
                                onDismiss()
                            }
                            is LyricsTranslationHelper.ManualImportResult.SourceMismatch -> {
                                importErrorMessage = context.getString(R.string.source_lyrics_changed)
                            }
                            is LyricsTranslationHelper.ManualImportResult.Error -> {
                                importErrorMessage = result.throwable.message ?: context.getString(R.string.ai_error_unknown)
                            }
                        }
                    }
                },
            ) {
                if (isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.import_translation))
                }
            }
        },
    ) {
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showLanguageDialog = true },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.ai_target_language),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = targetLanguageName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showModeDialog = true },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.ai_translation_mode),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = when (selectedMode) {
                        "Transcribed" -> stringResource(R.string.ai_translation_transcribed)
                        else -> stringResource(R.string.ai_translation_literal)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.lines_count, sourceSnapshot.sourceLines.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Lyrics Prompt", generatedPrompt)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, R.string.prompt_copied, Toast.LENGTH_SHORT).show()
                },
            ) {
                Icon(
                    painter = painterResource(R.drawable.content_copy),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.copy_prompt))
            }

            FilledTonalButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, generatedPrompt)
                    }
                    val chooser = Intent.createChooser(sendIntent, context.getString(R.string.share_prompt))
                    context.startActivity(chooser)
                },
            ) {
                Icon(
                    painter = painterResource(R.drawable.share),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.share_prompt))
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = responseText,
            onValueChange = {
                responseText = it
                importErrorMessage = null
            },
            label = { Text(stringResource(R.string.paste_ai_response)) },
            placeholder = { Text(stringResource(R.string.paste_json_hint)) },
            minLines = 5,
            maxLines = 10,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))

        if (importErrorMessage != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.error),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = importErrorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        } else if (responseText.text.isNotBlank()) {
            when (validationResult) {
                is LyricsTranslationFormat.ValidationResult.Success -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.check),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(
                                R.string.translation_is_valid,
                                validationResult.lines.size,
                                sourceSnapshot.sourceLines.size,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                is LyricsTranslationFormat.ValidationResult.Error -> {
                    val errorText = when (val reason = validationResult.reason) {
                        is LyricsTranslationFormat.ValidationError.EmptyInput ->
                            stringResource(R.string.empty_ai_response)
                        is LyricsTranslationFormat.ValidationError.InvalidJson ->
                            stringResource(R.string.invalid_translation_json)
                        is LyricsTranslationFormat.ValidationError.MissingLinesArray ->
                            stringResource(R.string.missing_lines_array)
                        is LyricsTranslationFormat.ValidationError.NonStringElement ->
                            stringResource(R.string.non_string_element)
                        is LyricsTranslationFormat.ValidationError.EmbeddedNewline ->
                            stringResource(R.string.embedded_newline_rejected, reason.lineIndex + 1)
                        is LyricsTranslationFormat.ValidationError.UnexpectedProperties ->
                            stringResource(R.string.extra_properties_rejected, reason.extraKeys.joinToString())
                        is LyricsTranslationFormat.ValidationError.AllLinesBlank ->
                            stringResource(R.string.all_lines_blank_rejected)
                        is LyricsTranslationFormat.ValidationError.LineCountMismatch ->
                            stringResource(R.string.line_count_mismatch, reason.expected, reason.received)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.error),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = errorText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}
