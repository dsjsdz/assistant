package com.awish.assistant.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.awish.assistant.R


@Composable
fun W51xSelectorDialog(
  count: Int = 10,
  defaultValue: Int?,
  onSelect: (Int) -> Unit,
) {
  var addr by remember(defaultValue) {
    mutableStateOf(defaultValue)
  }
  val rows = (1..count).chunked(5)

  AlertDialog(
    onDismissRequest = { },
    title = { Text("选择编号") },
    text = {
      Column {
        rows.forEach { rowItems ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            rowItems.forEach { id ->
              Card(
                modifier = Modifier
                  .weight(1f)
                  .aspectRatio(1f)
                  .clickable { addr = id },
                colors = CardDefaults.cardColors(
                  containerColor = if (addr == id) Color(0xFF448AFF) else Color(0xFFEEEEEE)
                ),
                border = BorderStroke(1.dp, Color.Blue.copy(0.1f)),
              ) {
                Box(
                  modifier = Modifier.fillMaxSize(),
                  contentAlignment = Alignment.Center
                ) {
                  Text(text = "$id")
                }
              }
            }

            // 如果不足4个，补空占位保持布局整齐
            repeat(4 - rowItems.size) {
              Spacer(modifier = Modifier.weight(1f))
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          addr?.let {
            onSelect(it)
          }
        },
        enabled = addr != null
      ) {
        Text(stringResource(R.string.btn_confirm))
      }
    },
    dismissButton = {}
  )
}
