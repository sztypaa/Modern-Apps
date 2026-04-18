package com.vayunmathur.health.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vayunmathur.health.R
import com.vayunmathur.health.Route
import com.vayunmathur.health.data.RecordType
import com.vayunmathur.health.util.HealthAPI
import com.vayunmathur.library.ui.IconNavigation
import com.vayunmathur.library.util.NavBackStack
import com.vayunmathur.library.util.round
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

data class NutrientDV(
    val name: String,
    val type: RecordType,
    val dailyValue: Double,
    val unit: String,
    val sumFunction: @Composable (RecordType, kotlinx.datetime.Instant, kotlinx.datetime.Instant) -> kotlinx.coroutines.flow.Flow<Double>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionDetailsPage(backStack: NavBackStack<Route>) {
    val dayStart = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.atStartOfDayIn(TimeZone.currentSystemDefault())
    val dayEnd = dayStart.plus(24.hours)

    val nutrients = listOf(
        NutrientDV("Calories", RecordType.Nutrition, 2000.0, "kcal") { t, s, e -> HealthAPI.sumInRange(t, s, e) },
        NutrientDV("Protein", RecordType.Nutrition, 50.0, "g") { t, s, e -> HealthAPI.sumProteinInRange(t, s, e) },
        NutrientDV("Carbohydrates", RecordType.Nutrition, 275.0, "g") { t, s, e -> HealthAPI.sumCarbsInRange(t, s, e) },
        NutrientDV("Fat", RecordType.Nutrition, 78.0, "g") { t, s, e -> HealthAPI.sumFatInRange(t, s, e) },
        NutrientDV("Fiber", RecordType.Nutrition, 28.0, "g") { t, s, e -> HealthAPI.sumFiberInRange(t, s, e) },
        NutrientDV("Sugar", RecordType.Nutrition, 50.0, "g") { t, s, e -> HealthAPI.sumSugarInRange(t, s, e) },
        NutrientDV("Sodium", RecordType.Nutrition, 2300.0, "mg") { t, s, e -> HealthAPI.sumSodiumInRange(t, s, e) },
        NutrientDV("Cholesterol", RecordType.Nutrition, 300.0, "mg") { t, s, e -> HealthAPI.sumCholesterolInRange(t, s, e) },
        NutrientDV("Saturated Fat", RecordType.Nutrition, 20.0, "g") { t, s, e -> HealthAPI.sumSaturatedFatInRange(t, s, e) },
        NutrientDV("Trans Fat", RecordType.Nutrition, 2.0, "g") { t, s, e -> HealthAPI.sumTransFatInRange(t, s, e) },
        NutrientDV("Vitamin A", RecordType.Nutrition, 900.0, "µg") { t, s, e -> HealthAPI.sumVitaminAInRange(t, s, e) },
        NutrientDV("Vitamin C", RecordType.Nutrition, 90.0, "mg") { t, s, e -> HealthAPI.sumVitaminCInRange(t, s, e) },
        NutrientDV("Vitamin D", RecordType.Nutrition, 20.0, "µg") { t, s, e -> HealthAPI.sumVitaminDInRange(t, s, e) },
        NutrientDV("Vitamin E", RecordType.Nutrition, 15.0, "mg") { t, s, e -> HealthAPI.sumVitaminEInRange(t, s, e) },
        NutrientDV("Vitamin K", RecordType.Nutrition, 120.0, "µg") { t, s, e -> HealthAPI.sumVitaminKInRange(t, s, e) },
        NutrientDV("Vitamin B6", RecordType.Nutrition, 1.7, "mg") { t, s, e -> HealthAPI.sumVitaminB6InRange(t, s, e) },
        NutrientDV("Vitamin B12", RecordType.Nutrition, 2.4, "µg") { t, s, e -> HealthAPI.sumVitaminB12InRange(t, s, e) },
        NutrientDV("Thiamin", RecordType.Nutrition, 1.2, "mg") { t, s, e -> HealthAPI.sumThiaminInRange(t, s, e) },
        NutrientDV("Riboflavin", RecordType.Nutrition, 1.3, "mg") { t, s, e -> HealthAPI.sumRiboflavinInRange(t, s, e) },
        NutrientDV("Niacin", RecordType.Nutrition, 16.0, "mg") { t, s, e -> HealthAPI.sumNiacinInRange(t, s, e) },
        NutrientDV("Folate", RecordType.Nutrition, 400.0, "µg") { t, s, e -> HealthAPI.sumFolateInRange(t, s, e) },
        NutrientDV("Biotin", RecordType.Nutrition, 30.0, "µg") { t, s, e -> HealthAPI.sumBiotinInRange(t, s, e) },
        NutrientDV("Pantothenic Acid", RecordType.Nutrition, 5.0, "mg") { t, s, e -> HealthAPI.sumPantothenicAcidInRange(t, s, e) },
        NutrientDV("Calcium", RecordType.Nutrition, 1300.0, "mg") { t, s, e -> HealthAPI.sumCalciumInRange(t, s, e) },
        NutrientDV("Iron", RecordType.Nutrition, 18.0, "mg") { t, s, e -> HealthAPI.sumIronInRange(t, s, e) },
        NutrientDV("Magnesium", RecordType.Nutrition, 420.0, "mg") { t, s, e -> HealthAPI.sumMagnesiumInRange(t, s, e) },
        NutrientDV("Phosphorus", RecordType.Nutrition, 1250.0, "mg") { t, s, e -> HealthAPI.sumPhosphorusInRange(t, s, e) },
        NutrientDV("Iodine", RecordType.Nutrition, 150.0, "µg") { t, s, e -> HealthAPI.sumIodineInRange(t, s, e) },
        NutrientDV("Zinc", RecordType.Nutrition, 11.0, "mg") { t, s, e -> HealthAPI.sumZincInRange(t, s, e) },
        NutrientDV("Selenium", RecordType.Nutrition, 55.0, "µg") { t, s, e -> HealthAPI.sumSeleniumInRange(t, s, e) },
        NutrientDV("Copper", RecordType.Nutrition, 0.9, "mg") { t, s, e -> HealthAPI.sumCopperInRange(t, s, e) },
        NutrientDV("Manganese", RecordType.Nutrition, 2.3, "mg") { t, s, e -> HealthAPI.sumManganeseInRange(t, s, e) },
        NutrientDV("Chromium", RecordType.Nutrition, 35.0, "µg") { t, s, e -> HealthAPI.sumChromiumInRange(t, s, e) },
        NutrientDV("Molybdenum", RecordType.Nutrition, 45.0, "µg") { t, s, e -> HealthAPI.sumMolybdenumInRange(t, s, e) },
        NutrientDV("Chloride", RecordType.Nutrition, 2300.0, "mg") { t, s, e -> HealthAPI.sumChlorideInRange(t, s, e) },
        NutrientDV("Potassium", RecordType.Nutrition, 4700.0, "mg") { t, s, e -> HealthAPI.sumPotassiumInRange(t, s, e) },
        NutrientDV("Caffeine", RecordType.Nutrition, 400.0, "mg") { t, s, e -> HealthAPI.sumCaffeineInRange(t, s, e) }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nutrition Breakdown") },
                navigationIcon = { IconNavigation(backStack) }
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = padding,
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(nutrients) { nutrient ->
                NutrientProgressCard(nutrient, dayStart, dayEnd)
            }
        }
    }
}

@Composable
fun NutrientProgressCard(nutrient: NutrientDV, start: kotlinx.datetime.Instant, end: kotlinx.datetime.Instant) {
    val currentAmount by nutrient.sumFunction(nutrient.type, start, end).collectAsState(0.0)
    val progress = (currentAmount / nutrient.dailyValue).toFloat().coerceIn(0f, 1f)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = nutrient.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = "${currentAmount.round(1)} / ${nutrient.dailyValue} ${nutrient.unit}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = if (progress >= 1f) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
    }
}
