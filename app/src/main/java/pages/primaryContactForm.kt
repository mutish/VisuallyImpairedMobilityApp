package pages

import retrofit2.Call
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
import androidx.navigation.NavController
import APIs.PrimaryContactRequest
import com.example.newapp.Routes
import APIs.primaryContactApiClient
import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.provider.ContactsContract
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.app.ComponentActivity



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactFormScreen(navController: NavController) {
    val context = LocalContext.current
   // val activity = LocalContext.current as ComponentActivity
    val tts = remember { TextToSpeech(context) { } }

    // Primary Contact
    var primaryName by remember { mutableStateOf("") }
    var primaryPhone by remember { mutableStateOf("") }
    var primaryNameError by remember { mutableStateOf<String?>(null) }
    var primaryPhoneError by remember { mutableStateOf<String?>(null) }


    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
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


    fun validateForm(): Boolean {
        var isValid = true

        // Validate primary contact
        if (primaryName.isBlank()) {
            primaryNameError = "Full name is required"
            isValid = false
        } else {
            primaryNameError = null
        }

        if (!primaryPhone.matches(Regex("^\\d{10}$"))) {
            primaryPhoneError = "Enter a valid 10-digit phone number"
            isValid = false
        } else {
            primaryPhoneError = null
        }

        return isValid
    }

    fun submitContact() {
        if (!validateForm()) return

        val newContact = PrimaryContactRequest(
            contact_name = primaryName,
            contact_phone = primaryPhone,
            relationship = "Primary"
        )

        primaryContactApiClient.api.createPrimaryContact(newContact).enqueue(object : retrofit2.Callback<Void> {
            override fun onResponse(call: Call<Void>, response: retrofit2.Response<Void>) {
                if (response.isSuccessful) {
                    Toast.makeText(context, "Contact saved!", Toast.LENGTH_SHORT).show()
                    navController.navigate(Routes.SecondaryContactForm)
                } else {
                    val errorBody = response.errorBody()?.string()
                    Toast.makeText(context, "Failed to save contact: $errorBody", Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                Toast.makeText(context, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
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
                primaryPhone = phoneNumber.replace("\\s".toRegex(), "")
                tts.speak("Phone number for $name found as $primaryPhone", TextToSpeech.QUEUE_FLUSH, null, null)
            } else {
                tts.speak("No phone number found for $name", TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }
        cursor?.close()
    }

    fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say the full name of the primary contact")
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    primaryName = matches[0]
                    fetchPhoneNumber(primaryName)
                }
            }
            override fun onError(error: Int) { Toast.makeText(context, "Speech Error", Toast.LENGTH_SHORT).show() }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer.startListening(intent)
    }

    fun goHome() {
        tts.speak("Navigating back to homepage", TextToSpeech.QUEUE_FLUSH, null, null)
        navController.popBackStack()
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .statusBarsPadding()
            .pointerInput(Unit){
                detectTapGestures(
                    onDoubleTap = {submitContact()},
                   // onSwipeRight = {goHome()}
                )
            },
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Emergency Primary Contact",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            textAlign = TextAlign.Center
        )
        Text("Primary Contact", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = primaryName,
            onValueChange = { primaryName = it },
            label = { Text("Full Name") },
            isError = primaryNameError != null,
            trailingIcon = {
                IconButton(onClick = { startVoiceInput() }) {
                    Icon(Icons.Filled.Done, contentDescription = "Voice Input")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        if (primaryNameError != null) {
            Text(primaryNameError!!, color = MaterialTheme.colorScheme.error)
        }

        OutlinedTextField(
            value = primaryPhone,
            onValueChange = { primaryPhone = it },
            label = { Text("Phone Number") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = primaryPhoneError != null,
            modifier = Modifier.fillMaxWidth()
        )
        if (primaryPhoneError != null) {
            Text(primaryPhoneError!!, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { submitContact()
                navController.navigate(Routes.SecondaryContactForm) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save contact")
        }
    }
}