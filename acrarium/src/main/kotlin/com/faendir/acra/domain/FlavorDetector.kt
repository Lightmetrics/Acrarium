/*
 * (C) Copyright 2026 Lukas Morawietz (https://github.com/F43nd1r)
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
package com.faendir.acra.domain

import org.springframework.stereotype.Component

@Component
class FlavorDetector {

    fun detectFlavor(phoneModel: String): String {
        val normalized = phoneModel
            .lowercase()
            .trim()
            .replace("_", "-")

        return when {
            normalized.startsWith("jimi-jc450") ->
                "jimimultichannelcam"

            normalized.startsWith("jimi") ->
                "jimi"

            normalized.startsWith("mitac") ->
                "mitac"

            normalized.startsWith("stsunlab") ->
                "stsunlab"

            normalized.startsWith("micronet") ->
                "micronet"

            else ->
                "default"
        }
    }

    fun suggestFlavors(phoneModels: List<String>): List<String> {
        val detected = phoneModels
            .filter { it.isNotBlank() }
            .map { detectFlavor(it) }
            .distinct()
            .sorted()

        return buildList {
            add("default")
            addAll(detected.filter { it != "default" })
        }
    }
}



