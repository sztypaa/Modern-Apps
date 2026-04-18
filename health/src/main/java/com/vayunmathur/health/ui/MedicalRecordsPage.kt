package com.vayunmathur.health.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.feature.ExperimentalPersonalHealthRecordApi
import androidx.health.connect.client.records.MedicalResource
import com.vayunmathur.library.util.NavBackStack
import com.vayunmathur.health.util.HealthAPI
import com.vayunmathur.health.R
import com.vayunmathur.health.Route
import com.vayunmathur.health.data.Patient
import com.vayunmathur.health.data.displayString
import com.vayunmathur.library.ui.IconNavigation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPersonalHealthRecordApi::class)
@Composable
fun MedicalRecordsPage(backStack: NavBackStack<Route>) {
    var patients by remember { mutableStateOf(listOf<Patient>()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            patients = HealthAPI.allMedicalRecords(MedicalResource.MEDICAL_RESOURCE_TYPE_PERSONAL_DETAILS).map {
                JSON.decodeFromString<Patient>(it.fhirResource.data)
            }
            println(patients)
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.section_medical_records)) },
            navigationIcon = {
                IconNavigation(backStack)
            }
        )
    }) { paddingValues ->
        Column(Modifier.padding(paddingValues).padding(horizontal = 16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                patients.forEach {
                    PatientCard(it)
                }
            }
        }
    }
}

@Composable
fun PatientCard(patient: Patient) {
    val nameString = patient.name.firstOrNull()?.displayString()
    val addressString = patient.address.firstOrNull()?.displayString()
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.Icon(
                painterResource(R.drawable.baseline_favorite_24),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(nameString ?: stringResource(R.string.unknown), style = MaterialTheme.typography.titleLarge)
                if (patient.gender != null)
                    Text(patient.gender.displayString(), style = MaterialTheme.typography.bodyMedium)
                if (patient.birthDate != null)
                    Text(patient.birthDate.displayString(), style = MaterialTheme.typography.bodyMedium)
                if (addressString != null)
                    Text(addressString, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}