/*
 * (C) Copyright 2020-2026 Lukas Morawietz (https://github.com/F43nd1r)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.faendir.acra.ui.component.dialog

import com.faendir.acra.domain.FlavorDetector
import com.faendir.acra.i18n.Messages
import com.faendir.acra.persistence.app.AppId
import com.faendir.acra.persistence.report.ReportRepository
import com.faendir.acra.persistence.version.Version
import com.faendir.acra.persistence.version.VersionRepository
import com.faendir.acra.ui.component.Translatable
import com.vaadin.flow.component.combobox.ComboBox
import com.vaadin.flow.component.orderedlayout.FlexLayout

class VersionEditorDialog(
    private val versionRepository: VersionRepository,
    private val reportRepository: ReportRepository,   
    private val flavorDetector: FlavorDetector,       
    private val appId: AppId,
    private val onUpdate: (() -> Unit) = {},
    private val old: Version? = null
) : AcrariumDialog() {

    init {
        header(if (old == null) Messages.NEW_VERSION else Messages.EDIT_VERSION)

        val code = Translatable.createNumberField(Messages.VERSION_CODE).with {
            value = old?.code?.toDouble()
                ?: versionRepository.getMaxVersionCode(appId)?.plus(1.0)
                ?: 1.0
            step = 1.0
            min = 1.0
            isStepButtonsVisible = true
            setWidthFull()
            if (old != null) isEnabled = false
        }

        val initialFlavors: List<String> = when {
            old != null -> {
                val phoneModels = reportRepository.getPhoneModelsForVersion(appId, old.code)
                flavorDetector.suggestFlavors(phoneModels)
            }
            code.value != null -> {
                val phoneModels = reportRepository.getPhoneModelsForVersion(appId, code.value.toInt())
                flavorDetector.suggestFlavors(phoneModels)
            }
            else -> listOf("default", "jimi", "jimimultichannelcam", "mitac", "stsunlab")
        }

        val flavor = ComboBox<String>(Messages.VERSION_FLAVOR).apply {
            setItems(initialFlavors)
            isAllowCustomValue = true
            placeholder = "Select device variant"
            value = old?.flavor?.takeIf { it.isNotBlank() } ?: "default"
            setWidthFull()
            if (old != null) isEnabled = false
        }

        code.addValueChangeListener { event ->
            if (event.value != null) {
                val phoneModels = reportRepository.getPhoneModelsForVersion(appId, event.value.toInt())
                val newOptions = flavorDetector.suggestFlavors(phoneModels)
                flavor.setItems(newOptions)
                if (flavor.value !in newOptions) {
                    flavor.value = "default"
                }
            }
        }

        val name = Translatable.createTextField(Messages.VERSION_NAME).with {
            if (old == null) isRequired = true else value = old.name
        }

        val upload = Translatable.createUploadField(Messages.MAPPING_FILE).with {
            setWidthFull()
            if (old != null) {
                value = old.mappings
            }
        }

        positiveAction(if (old == null) Messages.CREATE else Messages.SAVE) {
            versionRepository.setMappings(
                code = code.value.toInt(),
                flavor = flavor.value,
                name = name.value,
                appId = appId,
                mappings = upload.value
            )
            close()
            onUpdate.invoke()
        }

        negativeAction(Messages.CANCEL)

        val layout = FlexLayout(code, flavor, name, upload)
        layout.flexDirection = FlexLayout.FlexDirection.COLUMN
        layout.setWidthFull()

        add(layout)
    }
}
