package com.pr0gramm.app.ui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ButtonDefaults.textButtonColors
import androidx.compose.material.Card
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ListItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.RadioButton
import androidx.compose.material.RadioButtonDefaults
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.os.LocaleListCompat
import com.pr0gramm.app.R
import com.pr0gramm.app.ui.base.BaseDialogFragment
import org.xmlpull.v1.XmlPullParser
import java.util.Locale

class LanguagePickerDialog : BaseDialogFragment("LanguagePickerDialog") {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val supportedLocales = getSupportedLocales()

        return ComposeView(requireContext()).apply {
            setContent {
                DialogContent(supportedLocales)
            }
        }
    }

    @Composable
    fun DialogContent(
        supportedLocales: List<Locale>,
    ) {
        val checkedState = remember { mutableIntStateOf(-1) }

        Dialog(
            onDismissRequest = { dismiss() }
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                val titleStyle = MaterialTheme.typography.subtitle1
                val subtitleStyle = MaterialTheme.typography.body2
                Column {
                    ProvideTextStyle(titleStyle) {
                        Text(
                            stringResource(R.string.language_picker_title),
                            Modifier
                                .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                                .align(Alignment.Start),
                        )
                    }
                    ProvideTextStyle(subtitleStyle) {
                        Text(
                            stringResource(R.string.language_picker_subtitle),
                            Modifier
                                .padding(vertical = 4.dp, horizontal = 16.dp)
                                .align(Alignment.Start),
                        )
                    }
                    supportedLocales.forEachIndexed { idx, it ->
                        LanguageItem(
                            it,
                            selected = idx == checkedState.intValue,
                            onClick = {
                                checkedState.intValue = idx
                            },
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = { dismiss() },
                            modifier = Modifier.padding(8.dp),
                            colors = textButtonColors(
                                contentColor = colorResource(R.color.orange_primary)
                            ),
                        ) {
                            Text(stringResource(R.string.language_picker_dismiss))
                        }
                        TextButton(
                            onClick = {
                                val stateIdx = checkedState.intValue
                                if (stateIdx != -1) {
                                    val locale = supportedLocales[stateIdx]
                                    AppCompatDelegate.setApplicationLocales(
                                        LocaleListCompat.create(
                                            locale
                                        )
                                    )
                                    activity?.recreate()
                                }
                                dismiss()
                            },
                            enabled = checkedState.intValue != -1,
                            modifier = Modifier.padding(8.dp),
                            colors = textButtonColors(
                                contentColor = colorResource(R.color.orange_primary)
                            ),
                        ) {
                            Text(stringResource(R.string.language_picker_confirm))
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    fun LanguageItem(
        locale: Locale,
        selected: Boolean,
        onClick: (() -> Unit),
    ) {
        ListItem(
            modifier = Modifier.clickable {
                onClick()
            },
            text = { Text(locale.getDisplayLanguage(locale)) },
            trailing = {
                RadioButton(
                    selected = selected,
                    onClick = onClick,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = colorResource(R.color.orange_primary)
                    )
                )
            }
        )
    }

    private fun getSupportedLocales(): List<Locale> {
        val locales = mutableListOf<Locale>()

        try {
            val parser = resources.getXml(R.xml.locales_config)
            var eventType = parser.eventType
            val namespace = "http://schemas.android.com/apk/res/android"

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "locale") {
                    val languageTag = parser.getAttributeValue(namespace, "name")
                    if (languageTag != null) {
                        val locale = Locale.forLanguageTag(languageTag)
                        locales.add(locale)
                    }
                }
                eventType = parser.next()
            }

            parser.close()
        } catch (e: Exception) {
            logger.error("Error parsing locales config!", e)
        }

        return locales
    }
}