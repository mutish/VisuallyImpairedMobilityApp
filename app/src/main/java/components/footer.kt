package components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.input.pointer.pointerInput
import androidx.navigation.NavController
import com.example.newapp.R
import com.example.newapp.Routes

@Composable
fun Footer(navController: NavController) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .height(80.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FooterItem("Home", R.drawable.home_icon,) { navController.navigate(Routes.homeScreen) }
        FooterItem("Navigate", R.drawable.navigate_icon) { navController.navigate(Routes.navigationPage) }
        FooterItem("Matatu", R.drawable.bus_icon) { navController.navigate(Routes.MatatuPage) }
        FooterItem("Profile", R.drawable.person_icon) { navController.navigate(Routes.profilePage) }
    }
}

@Composable
fun FooterItem(label: String, iconRes: Int, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(4.dp)
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = label,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Fit
        )
        Text(text = label, fontSize = 14.sp, color = Color.Black)

    }
}
