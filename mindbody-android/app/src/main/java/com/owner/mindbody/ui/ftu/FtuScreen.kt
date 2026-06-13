package com.owner.mindbody.ui.ftu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.owner.mindbody.ui.components.PremiumCard
import com.owner.mindbody.ui.components.SectionHeader
import com.owner.mindbody.ui.theme.CardTitle
import com.owner.mindbody.ui.theme.MindBodyColors
import com.owner.mindbody.ui.theme.MindBodyShapes
import com.owner.mindbody.ui.theme.PageTitle
import com.owner.mindbody.ui.theme.StatLabel
import com.polar.sdk.api.model.PolarFirstTimeUseConfig

@Composable
fun FtuScreen(
    deviceId: String,
    onDone: () -> Unit,
    viewModel: FtuViewModel = viewModel()
) {
    val form by viewModel.form.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MindBodyColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader(
            eyebrow = "POLAR FTU",
            title = if (form.success) "配置完成" else "首次使用配置"
        )

        if (form.success) {
            PremiumCard {
                Text(text = "首次配置已完成！", style = PageTitle.copy(fontSize = PageTitle.fontSize))
                Text(
                    text = "Polar Loop 现在可以正常采集心率与活动数据。",
                    style = StatLabel.copy(fontSize = CardTitle.fontSize * 0.9f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                Button(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MindBodyColors.PrimaryIndigo),
                    shape = MindBodyShapes.RadioOption
                ) {
                    Text("返回")
                }
            }
            return@Column
        }

        PremiumCard {
            Text(
                text = "Polar Loop 需要配置身体基础数据后才能工作。以下信息会写入手环。",
                style = StatLabel.copy(fontSize = CardTitle.fontSize * 0.9f)
            )
            Text(
                text = "设备 ID：$deviceId",
                style = StatLabel.copy(color = MindBodyColors.PrimaryIndigo),
                modifier = Modifier.padding(top = 8.dp)
            )

            Text(text = "生理性别", style = CardTitle, modifier = Modifier.padding(top = 16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = form.gender == PolarFirstTimeUseConfig.Gender.MALE,
                    onClick = { viewModel.updateGender(PolarFirstTimeUseConfig.Gender.MALE) },
                    label = { Text("男") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MindBodyColors.PrimaryIndigoLight,
                        selectedLabelColor = MindBodyColors.PrimaryIndigo
                    )
                )
                FilterChip(
                    selected = form.gender == PolarFirstTimeUseConfig.Gender.FEMALE,
                    onClick = { viewModel.updateGender(PolarFirstTimeUseConfig.Gender.FEMALE) },
                    label = { Text("女") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MindBodyColors.PrimaryIndigoLight,
                        selectedLabelColor = MindBodyColors.PrimaryIndigo
                    )
                )
            }

            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MindBodyColors.PrimaryIndigo,
                focusedLabelColor = MindBodyColors.PrimaryIndigo,
                cursorColor = MindBodyColors.PrimaryIndigo
            )

            OutlinedTextField(
                value = form.birthYear.toString(),
                onValueChange = { v -> v.toIntOrNull()?.let { viewModel.updateBirthYear(it) } },
                label = { Text("出生年份") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                colors = fieldColors,
                shape = MindBodyShapes.RadioOption
            )
            OutlinedTextField(
                value = form.heightCm,
                onValueChange = viewModel::updateHeight,
                label = { Text("身高 (cm)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors,
                shape = MindBodyShapes.RadioOption
            )
            OutlinedTextField(
                value = form.weightKg,
                onValueChange = viewModel::updateWeight,
                label = { Text("体重 (kg)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors,
                shape = MindBodyShapes.RadioOption
            )
            OutlinedTextField(
                value = form.restingHr,
                onValueChange = viewModel::updateRestingHr,
                label = { Text("静息心率 (bpm)，默认 60") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors,
                shape = MindBodyShapes.RadioOption
            )

            form.errorMessage?.let {
                Text(
                    it,
                    color = MindBodyColors.HeartRed,
                    style = StatLabel,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Button(
                onClick = { viewModel.submit(deviceId) },
                enabled = !form.isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MindBodyColors.PrimaryIndigo),
                shape = MindBodyShapes.RadioOption
            ) {
                if (form.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        color = MindBodyColors.Background
                    )
                }
                Text("写入手环并完成配置")
            }
        }
    }
}
