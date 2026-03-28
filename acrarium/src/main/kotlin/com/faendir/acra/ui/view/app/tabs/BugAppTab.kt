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
package com.faendir.acra.ui.view.app.tabs

import com.faendir.acra.i18n.Messages
import com.vdurmont.semver4j.Semver
import com.faendir.acra.navigation.RouteParams
import com.faendir.acra.navigation.View
import com.faendir.acra.persistence.bug.BugRepository
import com.faendir.acra.persistence.bug.BugStats
import com.faendir.acra.persistence.user.Permission
import com.faendir.acra.persistence.version.VersionRepository
import com.faendir.acra.security.SecurityUtils
import com.faendir.acra.settings.LocalSettings
import com.faendir.acra.ui.component.BugSolvedVersionSelect
import com.faendir.acra.ui.component.Translatable
import com.faendir.acra.ui.component.dialog.createButton
import com.faendir.acra.ui.component.dialog.showFluentDialog
import com.faendir.acra.ui.component.grid.BasicLayoutPersistingFilterableGridView
import com.faendir.acra.ui.component.grid.column
import com.faendir.acra.ui.component.grid.renderer.InstantRenderer
import com.faendir.acra.ui.component.grid.renderer.VersionRenderer
import com.faendir.acra.ui.view.app.AppView
import com.faendir.acra.ui.view.bug.BugView
import com.faendir.acra.ui.view.bug.tabs.ReportBugTab
import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.grid.GridSortOrder
import com.vaadin.flow.component.notification.Notification
import com.vaadin.flow.component.radiobutton.RadioButtonGroup
import com.vaadin.flow.data.renderer.ComponentRenderer
import com.vaadin.flow.router.Route

@View
@Route(value = "bug", layout = AppView::class)
class BugAppTab(
    private val bugRepository: BugRepository,
    private val versionRepository: VersionRepository,
    private val localSettings: LocalSettings,
    private val routeParams: RouteParams,
) : Composite<BasicLayoutPersistingFilterableGridView<BugStats, BugStats.Filter, BugStats.Sort>>() {
    override fun initContent(): BasicLayoutPersistingFilterableGridView<BugStats, BugStats.Filter, BugStats.Sort> {
        val appId = routeParams.appId()
        val gridView = BasicLayoutPersistingFilterableGridView(bugRepository.getProvider(appId), localSettings::bugGridSettings) {
            column({ it.reportCount }) {
                setSortable(BugStats.Sort.REPORT_COUNT)
                setCaption(Messages.REPORTS)
                flexGrow = 0
                width = "100px"
            }
            
            column({ it.title }) {
                setSortable(BugStats.Sort.TITLE)
                setFilterableContains({ BugStats.Filter.TITLE(it) }, Messages.TITLE)
                setCaption(Messages.TITLE)
                isAutoWidth = true
            }
            
            val versions = versionRepository.getVersionNames(appId)
            val versionMap = versions.associateBy { it.code to it.flavor }
            
            column(VersionRenderer(versions) { it.latestVersionKey }) {
                setComparator { bug1, bug2 ->
                    val v1 = versionMap[bug1.latestVersionKey.code to bug1.latestVersionKey.flavor]
                    val v2 = versionMap[bug2.latestVersionKey.code to bug2.latestVersionKey.flavor]
                    
                    if (v1 == null || v2 == null) {
                        bug1.latestVersionKey.code.compareTo(bug2.latestVersionKey.code)
                    } else {
                        try {
                            Semver(v1.name, Semver.SemverType.LOOSE)
                                .compareTo(Semver(v2.name, Semver.SemverType.LOOSE))
                        } catch (e: Exception) {
                            bug1.latestVersionKey.code.compareTo(bug2.latestVersionKey.code)
                        }
                    }
                }
                setFilterableIs(versions, { it.name }, { BugStats.Filter.LATEST_VERSION(it.code, it.flavor) }, Messages.APP_VERSION)
                setCaption(Messages.LATEST_VERSION)
                flexGrow = 0
                width = "150px"
            }
            
            column({ it.affectedInstallations }) {
                setSortable(BugStats.Sort.AFFECTED_INSTALLATIONS)
                setCaption(Messages.AFFECTED_INSTALLATIONS)
                sort(GridSortOrder.desc(this).build())
                flexGrow = 0
                width = "180px"
            }
            
            column({ it.affectedVersions }) {
                setSortable(BugStats.Sort.AFFECTED_VERSIONS)
                setCaption(Messages.AFFECTED_VERSIONS)
                flexGrow = 0
                width = "150px"
            }
            
            column({ bug ->
                bug.mostAffectedVersionKey?.let { versionKey ->
                    val versionName = versionRepository.find(appId, versionKey)?.name ?: "${versionKey.code}"
                    "$versionName (${bug.mostAffectedVersionCount})"
                } ?: "-"
            }) {
                setSortable(BugStats.Sort.MOST_AFFECTED_VERSION_COUNT)
                setCaption(Messages.MOST_AFFECTED_VERSION)
                flexGrow = 0
                width = "200px"
            }
            
            if (appId.value.toInt() == 5) {
                column({ it.affectedEngines }) {
                    setSortable(BugStats.Sort.AFFECTED_ENGINES)
                    setCaption(Messages.AFFECTED_ENGINES)
                    flexGrow = 0
                    width = "130px"
                }
                
                column({ bug ->
                    bug.mostAffectedEngine?.let { engine ->
                        "$engine (${bug.mostAffectedEngineCount})"
                    } ?: "-"
                }) {
                    setSortable(BugStats.Sort.MOST_AFFECTED_ENGINE_COUNT)
                    setCaption(Messages.MOST_AFFECTED_ENGINE)
                    flexGrow = 0
                    width = "180px"
                }
            }
            
            column(InstantRenderer { it.latestReport }) {
                setSortable(BugStats.Sort.LATEST_REPORT)
                setCaption(Messages.LATEST_REPORT)
                flexGrow = 0
                width = "150px"
            }
            
            column(ComponentRenderer { bug: BugStats -> BugSolvedVersionSelect(appId, bug, versions, bugRepository) }) {
                setSortable(BugStats.Sort.SOLVED_VERSION_CODE)
                setFilterableToggle(BugStats.Filter.IS_NOT_SOLVED_OR_REGRESSION, true, Messages.HIDE_SOLVED)
                setCaption(Messages.SOLVED)
                flexGrow = 0
                width = "150px"
            }
            
            addOnClickNavigation(ReportBugTab::class.java) { BugView.getNavigationParams(appId, it.id) }
        }
        
        if (SecurityUtils.hasPermission(appId, Permission.Level.EDIT)) {
            val mergeButton = Translatable.createButton(Messages.MERGE_BUGS) {
                val selectedItems: List<BugStats> = gridView.grid.selectedItems.toList()
                if (selectedItems.size > 1) {
                    val titles = RadioButtonGroup<String>()
                    titles.setItems(selectedItems.map { bug: BugStats -> bug.title })
                    titles.value = selectedItems[0].title
                    showFluentDialog {
                        header(Messages.CHOOSE_BUG_GROUP_TITLE)
                        add(titles)
                        createButton {
                            bugRepository.mergeBugs(appId, selectedItems.map { it.id }, titles.value)
                            gridView.grid.deselectAll()
                            gridView.grid.dataProvider.refreshAll()
                        }
                    }
                } else {
                    Notification.show(Messages.ONLY_ONE_BUG_SELECTED)
                }
            }.with {
                isEnabled = false
                removeThemeVariants(ButtonVariant.LUMO_PRIMARY)
            }
            gridView.grid.setSelectionMode(Grid.SelectionMode.MULTI)
            gridView.grid.asMultiSelect().addSelectionListener { mergeButton.content.isEnabled = it.allSelectedItems.size >= 2 }
            gridView.header.addComponentAsFirst(mergeButton)
        }
        return gridView
    }
}