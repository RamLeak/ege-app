package com.daniel.ege100.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniel.ege100.ui.theme.BgElevated2
import com.daniel.ege100.ui.theme.InterFamily
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelTertiary
import com.daniel.ege100.ui.theme.SystemBlue

/**
 * iOS-style TextField (STAGE_3_POLISH §Б7).
 *
 * Без OutlinedTextField'овой обводки и floating-label — просто закруглённый
 * tinted-фон с placeholder'ом, текст 17sp, курсор SystemBlue.
 */
@Composable
fun IosTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Text),
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val style = TextStyle(
        color = Label,
        fontSize = 17.sp,
        fontFamily = InterFamily,
        lineHeight = 22.sp,
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(SystemBlue),
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = LabelTertiary,
                    fontSize = 17.sp,
                    fontFamily = InterFamily,
                )
            }
            inner()
        },
        modifier = modifier
            .fillMaxWidth()
            .background(BgElevated2, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    )
}
