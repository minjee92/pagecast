package com.pagecast.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pagecast.app.ui.importer.ImportScreen
import com.pagecast.app.ui.library.LibraryScreen
import com.pagecast.app.ui.reader.ReaderScreen
import kotlinx.serialization.Serializable

@Serializable
object LibraryRoute

@Serializable
object ImportRoute

/** 화면 간에는 본문이 아니라 bookId만 넘긴다. 본문은 각 화면이 저장소에서 읽는다. */
@Serializable
data class ReaderRoute(val bookId: Long)

@Composable
fun PagecastNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = LibraryRoute) {
        composable<LibraryRoute> {
            LibraryScreen(
                onOpenBook = { bookId -> navController.navigate(ReaderRoute(bookId)) },
                onPasteText = { navController.navigate(ImportRoute) },
            )
        }
        composable<ImportRoute> {
            ImportScreen(
                onBack = { navController.popBackStack() },
                onImported = { bookId ->
                    navController.navigate(ReaderRoute(bookId)) {
                        popUpTo<LibraryRoute>()
                    }
                },
            )
        }
        composable<ReaderRoute> {
            ReaderScreen(onBack = { navController.popBackStack() })
        }
    }
}
