package com.gb6105.musictimely.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gb6105.musictimely.ui.theme.MtColors
import com.gb6105.musictimely.ui.theme.MtDimens
import com.gb6105.musictimely.ui.theme.MtType

/** Design "작업 필드" (350×58, r29, white): one line with a placeholder; grows with large text. */
@Composable
fun TaskField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    isError: Boolean = false,
) {
    val shape = RoundedCornerShape(MtDimens.FieldRadius)
    val fill = MaterialTheme.colorScheme.surfaceVariant
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MtType.button.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MtColors.Icon),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MtDimens.FieldHeight)
                    .softShadow(shape, fill, MtShadows.Field)
                    .background(fill, shape)
                    .then(if (isError) Modifier.border(1.6.dp, MtColors.Red, shape) else Modifier)
                    .padding(horizontal = MtDimens.ScreenPadding, vertical = 12.dp),
            ) {
                if (value.isEmpty()) Text(placeholder, style = MtType.button, color = MaterialTheme.colorScheme.onSurfaceVariant)
                inner()
            }
        },
    )
}
