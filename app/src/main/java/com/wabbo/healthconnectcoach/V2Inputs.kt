package com.wabbo.healthconnectcoach

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun V2LabeledInput(value: String,onValue: (String)->Unit,label: String,enabled: Boolean=true,keyboardOptions: KeyboardOptions=KeyboardOptions.Default) {
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(9.dp)) {
        RocoText(label,fontSize=12.sp,lineHeight=18.sp,color=Color(0xFF526745))
        Surface(color=Color.White,shape=RoundedCornerShape(15.dp),border=BorderStroke(1.dp,Color(0xFFCFDDBF)),modifier=Modifier.fillMaxWidth()) {
            BasicTextField(value,onValue,enabled=enabled,singleLine=true,keyboardOptions=keyboardOptions,cursorBrush=SolidColor(V2Green),textStyle=MaterialTheme.typography.bodyLarge.copy(fontSize=16.sp,lineHeight=24.sp,color=Color(0xFF455E32)),modifier=Modifier.fillMaxWidth().heightIn(min=54.dp).padding(horizontal=16.dp,vertical=14.dp).semantics {contentDescription=RocoStrings.translate(label)})
        }
    }
}
