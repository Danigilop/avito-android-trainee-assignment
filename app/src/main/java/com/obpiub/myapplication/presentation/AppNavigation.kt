package com.obpiub.myapplication.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.obpiub.myapplication.AppContainer

private enum class Tab(val route: String, val title: String, val symbol: String) {
    Notes("notes", "Заметки", "≡"),
    Tasks("tasks", "Задачи", "✓"),
    Settings("settings", "Настройки", "⚙"),
}

@Composable
fun AppNavigation(container: AppContainer, settingsViewModel: SettingsViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    Scaffold(
        bottomBar = {
            if (Tab.entries.any { it.route == route }) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Text(tab.symbol) },
                            label = { Text(tab.title) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Notes.route,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(Tab.Notes.route) {
                val viewModel: NotesViewModel = viewModel(factory = viewModelFactory {
                    initializer { NotesViewModel(container.noteRepository, createSavedStateHandle()) }
                })
                val state by viewModel.state.collectAsStateWithLifecycle()
                NotesScreen(state, viewModel) { id ->
                    navController.navigate("note/$id") { launchSingleTop = true }
                }
            }
            composable(
                route = "note/{noteId}",
                arguments = listOf(navArgument("noteId") { type = NavType.LongType }),
            ) {
                val viewModel: NoteEditorViewModel = viewModel(factory = viewModelFactory {
                    initializer { NoteEditorViewModel(container.noteRepository, createSavedStateHandle(), container.noteImageStore) }
                })
                val state by viewModel.state.collectAsStateWithLifecycle()
                val voice: VoiceViewModel = viewModel(factory = viewModelFactory {
                    initializer {
                        VoiceViewModel(container.createSpeechInput(), container.voiceRepository, false) { text ->
                            val previous = viewModel.state.value.text
                            viewModel.changeText(if (previous.isBlank()) text else "$previous\n$text")
                        }
                    }
                })
                val voiceState by voice.state.collectAsStateWithLifecycle()
                NoteEditorScreen(state, viewModel, voice, voiceState) { navController.popBackStack() }
            }
            composable(Tab.Tasks.route) {
                val viewModel: TasksViewModel = viewModel(factory = viewModelFactory {
                    initializer { TasksViewModel(container.taskRepository, createSavedStateHandle()) }
                })
                val state by viewModel.state.collectAsStateWithLifecycle()
                val voice: VoiceViewModel = viewModel(factory = viewModelFactory {
                    initializer {
                        VoiceViewModel(container.createSpeechInput(), container.voiceRepository, true) { title ->
                            container.taskRepository.saveTask(com.obpiub.myapplication.domain.model.Task(title = title))
                        }
                    }
                })
                val voiceState by voice.state.collectAsStateWithLifecycle()
                TasksScreen(state, viewModel, voice, voiceState)
            }
            composable(Tab.Settings.route) {
                val state by settingsViewModel.state.collectAsStateWithLifecycle()
                SettingsScreen(state, settingsViewModel)
            }
        }
    }
}
