package app.listenandwrite.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.listenandwrite.domain.Lang
import app.listenandwrite.domain.Subject

@Composable
fun ListenNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "subjects") {
        composable("subjects") {
            SubjectGate(
                onSubject = { subject -> nav.navigate("subject/${subject.name}") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable(
            route = "subject/{subject}",
            arguments = listOf(navArgument("subject") { type = NavType.StringType }),
        ) { entry ->
            val subject = subjectOf(entry.arguments?.getString("subject"))
            SubjectHomeScreen(
                subject = subject,
                onBack = { nav.popBackStack() },
                onOpenUnit = { listId, unit -> nav.navigate("unit/$listId/$unit") },
                onMistakes = { nav.navigate("mistakes/${if (subject == Subject.ENGLISH) Lang.EN.name else Lang.ZH.name}") },
                onDrills = { nav.navigate("drills") },
                onSession = { nav.navigate("session") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("drills") {
            DrillScreen(
                onBack = { nav.popBackStack() },
                onOpenUnit = { listId, unit -> nav.navigate("unit/$listId/$unit") },
                onSession = { nav.navigate("session") },
            )
        }
        composable("settings") {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
        composable(
            route = "mistakes/{lang}",
            arguments = listOf(navArgument("lang") { type = NavType.StringType }),
        ) { entry ->
            val lang = entry.arguments?.getString("lang") ?: Lang.ZH.name
            MistakeScreen(
                langName = lang,
                onBack = { nav.popBackStack() },
                onSession = { nav.navigate("session") },
            )
        }
        composable(
            route = "unit/{listId}/{unit}",
            arguments = listOf(
                navArgument("listId") { type = NavType.LongType },
                navArgument("unit") { type = NavType.IntType },
            ),
        ) { entry ->
            val listId = entry.arguments?.getLong("listId") ?: return@composable
            val unit = entry.arguments?.getInt("unit") ?: return@composable
            ListDetailScreen(
                listId = listId,
                fixedUnit = unit,
                onBack = { nav.popBackStack() },
                onReview = { nav.navigate("review/$listId") },
                onSession = { nav.navigate("session") },
            )
        }
        composable(
            route = "review/{listId}",
            arguments = listOf(navArgument("listId") { type = NavType.LongType }),
        ) { entry ->
            val listId = entry.arguments?.getLong("listId") ?: return@composable
            ReviewScreen(listId = listId, onBack = { nav.popBackStack() })
        }
        composable("session") {
            SessionScreen(
                onBack = { nav.popBackStack() },
                onFinished = { backRoute ->
                    nav.navigate("summary") {
                        popUpTo(backRoute) { inclusive = false }
                    }
                },
            )
        }
        composable("summary") {
            SummaryScreen(
                onBack = { nav.popBackStack() },
                onAgain = { nav.navigate("session") },
                onGrade = { nav.navigate("grade") },
            )
        }
        composable("grade") {
            GradeScreen(onBack = { nav.popBackStack() })
        }
    }
}

private fun subjectOf(name: String?): Subject {
    return if (name == Subject.ENGLISH.name) Subject.ENGLISH else Subject.CHINESE
}
