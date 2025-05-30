package pages

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.navigation.NavController
import com.example.newapp.Routes
import android.Manifest
import android.provider.ContactsContract
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import apis.GoogleAuthClient
import com.example.newapp.SQL.SC.SecondaryContact
import com.example.newapp.SQL.SC.sCViewModel
import com.example.newapp.SQL.users.UserViewModel
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SecondaryContactForm(navController: NavController, googleAuthClient: GoogleAuthClient) {
    val context = LocalContext.current
    var tts = remember { TextToSpeech(context) { } }
    val scope = rememberCoroutineScope()
    var secondaryName by remember { mutableStateOf("") }
    var secondaryPhone by remember { mutableStateOf("") }
    var secondaryNameError by remember { mutableStateOf<String?>(null) }
    var secondaryPhoneError by remember { mutableStateOf<String?>(null) }
    var contactSuggestions by remember { mutableStateOf(listOf<String>()) }
    var dbSaveSuccess by remember { mutableStateOf(true) }

    // Initialize ViewModels
    val userViewModel = remember {
        ViewModelProvider(
            context as ViewModelStoreOwner,
            ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as Application)
        ).get(UserViewModel::class.java)
    }

    val contactViewModel = remember {
        ViewModelProvider(
            context as ViewModelStoreOwner,
            ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as Application)
        ).get(sCViewModel::class.java)
    }

    // Get current user ID
    val currentFirebaseUUID = googleAuthClient.getUserId() ?: ""
    var currentUserID by remember { mutableStateOf(0) }

    // Load current user ID from Firebase UUID
    LaunchedEffect(currentFirebaseUUID) {
        if (currentFirebaseUUID.isNotEmpty()) {
            val user = userViewModel.getUserByFirebaseUUID(currentFirebaseUUID)
            if (user != null) {
                currentUserID = user.userID
                // Check if secondary contact already exists
                val existingContact = contactViewModel.getSecondaryContact(currentUserID)
                if (existingContact != null) {
                    secondaryName = existingContact.contactname
                    secondaryPhone = existingContact.contactnumber
                    Log.d("ContactForm", "Loaded existing secondary contact: $secondaryName")
                }
            }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                Toast.makeText(context, "Permission granted!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Permission denied!", Toast.LENGTH_SHORT).show()
            }
        }
    )

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
    }

    tts = remember {
        TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.getDefault()
                tts.speak("You are on the Secondary Emergency contact form", TextToSpeech.QUEUE_FLUSH, null, null)
            }

        }
    }
    // Cleanup TTS when the screen is removed
    DisposableEffect(Unit) {
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    fun validateForm(): Boolean {
        var isValid = true
        if (secondaryName.isBlank()) {
            secondaryNameError = "Full name is required"
            isValid = false
        } else {
            secondaryNameError = null
        }

        // Accept international formats like +2547123456789 or regular 10-digit numbers
        if (!secondaryPhone.matches(Regex("^(\\+\\d{1,15}|\\d{10,15})$"))) {
            secondaryPhoneError = "Enter a valid phone number (10+ digits, can include + prefix)"
            isValid = false
        } else {
            secondaryPhoneError = null
        }
        return isValid
    }

    fun submitContact() {
        if (!validateForm()) return

        // Save to SQLite
        scope.launch {
            try {
                if (currentUserID > 0) {
                    val contact = SecondaryContact(
                        userID = currentUserID,
                        contactname = secondaryName,
                        contactnumber = secondaryPhone
                    )

                    val result = contactViewModel.insertOrUpdateContact(contact)
                    if (result > 0) {
                        dbSaveSuccess = true
                        Log.d("ContactForm", "Secondary contact saved to SQLite: $secondaryName")
                        tts.speak("Secondary contact saved successfully", TextToSpeech.QUEUE_FLUSH, null, null)
                        navController.navigate(Routes.homeScreen)
                    } else {
                        dbSaveSuccess = false
                        Log.e("ContactForm", "Failed to save secondary contact to SQLite")
                        Toast.makeText(context, "Failed to save contact", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e("ContactForm", "Cannot save secondary contact: User not logged in or user ID not found")
                    Toast.makeText(context, "Please log in to save contacts", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                dbSaveSuccess = false
                Log.e("ContactForm", "Error saving secondary contact to SQLite: ${e.message}")
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun fetchSuggestions(query: String) {
        val contentResolver = context.contentResolver
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val cursor = contentResolver.query(
            uri,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$query%"),
            null
        )
        val suggestions = mutableListOf<String>()
        cursor?.use {
            while (it.moveToNext()) {
                val name = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                suggestions.add(name)
            }
        }
        contactSuggestions = suggestions.distinct()
        cursor?.close()
    }

    fun fetchPhoneNumber(name: String) {
        val contentResolver = context.contentResolver
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val cursor = contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} = ?",
            arrayOf(name),
            null
        )
        cursor?.use {
            if (it.moveToFirst()) {
                val phoneNumber = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                secondaryPhone = phoneNumber.replace("\\s".toRegex(), "")
                tts.speak("Phone number for $name", TextToSpeech.QUEUE_FLUSH, null, null)
            } else {
                tts.speak("No phone number found for $name", TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }
        cursor?.close()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .statusBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Emergency Secondary Contact",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Text("Secondary Contact", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = secondaryName,
            onValueChange = {
                secondaryName = it
                fetchSuggestions(it)
            },
            label = { Text("Full Name") },
            isError = secondaryNameError != null,
            modifier = Modifier
                .fillMaxWidth()
        )
        if (secondaryNameError != null) {
            Text(secondaryNameError!!, color = MaterialTheme.colorScheme.error)
        }

        DropdownMenu(
            expanded = contactSuggestions.isNotEmpty(),
            onDismissRequest = { contactSuggestions = emptyList() }
        ) {
            contactSuggestions.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        secondaryName = name
                        contactSuggestions = emptyList()
                        scope.launch { fetchPhoneNumber(name) }
                    }
                )
            }
        }

        OutlinedTextField(
            value = secondaryPhone,
            onValueChange = { secondaryPhone = it },
            label = { Text("Phone Number") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = secondaryPhoneError != null,
            modifier = Modifier
                .fillMaxWidth()
        )
        if (secondaryPhoneError != null) {
            Text(secondaryPhoneError!!, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                submitContact()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Submit")
        }
    }
}