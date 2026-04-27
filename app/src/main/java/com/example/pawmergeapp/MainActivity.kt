package com.example.pawmergeapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pawmergeapp.ui.theme.PawMergeAppTheme

data class DogTile(
    val tails: Int = 0,
    val breed: String = "",
    val locked: Boolean = false
)

data class AdoptionRequest(
    val adopterName: String,
    val requestedBreed: String,
    val requestedTails: Int,
    val reward: Int
)

fun getDogImage(breed: String, tails: Int): Int {
    return when (breed) {
        "Beagle" -> when (tails) {
            1 -> R.drawable.beagle_level_1
            2 -> R.drawable.beagle_level_2
            3 -> R.drawable.beagle_level_3
            4 -> R.drawable.beagle_level_4
            5 -> R.drawable.beagle_level_5
            else -> R.drawable.beagle_level_1
        }

        "German" -> when (tails) {
            1 -> R.drawable.german_level_1
            2 -> R.drawable.german_level_2
            3 -> R.drawable.german_level_3
            4 -> R.drawable.german_level_4
            5 -> R.drawable.german_level_5
            else -> R.drawable.german_level_1
        }

        else -> R.drawable.beagle_level_1
    }
}

fun generateRandomRequest(): AdoptionRequest {
    val breeds = listOf("Beagle", "German")
    val tails = listOf(2, 3, 4, 5).random()

    return AdoptionRequest(
        adopterName = listOf("Maya", "Chris", "Jordan", "Aaliyah", "Noah", "Sam").random(),
        requestedBreed = breeds.random(),
        requestedTails = tails,
        reward = tails * 125
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PawMergeAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFF7E6C4)
                ) {
                    PawMergeScreen()
                }
            }
        }
    }
}

@Composable
fun PawMergeScreen() {
    var coins by remember { mutableStateOf(100) }
    var energy by remember { mutableStateOf(100) }
    var gems by remember { mutableStateOf(25) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var currentRequest by remember { mutableStateOf(generateRandomRequest()) }
    var message by remember { mutableStateOf("Merge matching dogs to add tails!") }

    val board = remember {
        mutableStateListOf(
            DogTile(1, "Beagle"), DogTile(1, "Beagle"), DogTile(1, "German"), DogTile(), DogTile(1, "German"),
            DogTile(), DogTile(1, "Beagle"), DogTile(1, "German"), DogTile(1, "German"), DogTile(1, "Beagle"),
            DogTile(), DogTile(1, "Beagle"), DogTile(), DogTile(1, "German"), DogTile(1, "Beagle"),
            DogTile(1, "German"), DogTile(1, "Beagle"), DogTile(), DogTile(), DogTile(1, "German"),
            DogTile(), DogTile(), DogTile(1, "Beagle"), DogTile(), DogTile(),
            DogTile(1, "German"), DogTile(1, "German"), DogTile(), DogTile(1, "Beagle"), DogTile(1, "Beagle")
        )
    }

    fun checkAdoption(index: Int) {
        val dog = board[index]

        if (dog.breed == currentRequest.requestedBreed && dog.tails == currentRequest.requestedTails) {
            coins += currentRequest.reward
            board[index] = DogTile()
            message = "${currentRequest.adopterName} adopted a ${dog.tails}-tailed ${dog.breed}! +$${currentRequest.reward}"
            currentRequest = generateRandomRequest()
        }
    }

    fun onTileClicked(index: Int) {
        val clickedDog = board[index]

        if (clickedDog.locked || clickedDog.tails == 0) return

        if (selectedIndex == null) {
            selectedIndex = index
            message = "Selected ${clickedDog.breed} with ${clickedDog.tails} tail(s)."
        } else {
            val first = selectedIndex!!
            val second = index

            if (first == second) {
                selectedIndex = null
                message = "Selection canceled."
                return
            }

            val dogOne = board[first]
            val dogTwo = board[second]

            if (dogOne.breed == dogTwo.breed && dogOne.tails == dogTwo.tails && dogOne.tails > 0) {
                val newTailCount = dogOne.tails + 1

                board[first] = DogTile(
                    tails = newTailCount,
                    breed = dogOne.breed
                )

                board[second] = DogTile()

                coins += newTailCount * 10
                message = "Merged into a ${newTailCount}-tailed ${dogOne.breed}! +$${newTailCount * 10}"

                checkAdoption(first)
            } else {
                message = "Dogs must be the same breed and have the same tail count."
            }

            selectedIndex = null
        }
    }

    fun spawnDogFromHouse(breed: String) {
        val energyCost = 10

        if (energy < energyCost) {
            message = "Not enough energy!"
            return
        }

        val emptyIndex = board.indexOfFirst { it.tails == 0 && !it.locked }

        if (emptyIndex == -1) {
            message = "No empty spaces on the board!"
            return
        }

        energy -= energyCost
        board[emptyIndex] = DogTile(tails = 1, breed = breed)
        message = "Spawned a 1-tailed $breed!"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6E3BE))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .padding(bottom = 90.dp)
        ) {
            TopStatusBar(energy, coins, gems)

            Spacer(modifier = Modifier.height(10.dp))

            AdoptionCard(currentRequest)

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = message,
                color = Color(0xFF7A4E1D),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            DogHouseGrid { spawnDogFromHouse(it) }

            Spacer(modifier = Modifier.height(10.dp))

            GameBoard(board, selectedIndex) { onTileClicked(it) }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            BottomMenu()
        }
    }
}

@Composable
fun TopStatusBar(
    energy: Int,
    coins: Int,
    gems: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        LevelBadge(level = 1)

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ResourceChip("⚡", energy.toString())
            ResourceChip("🪙", coins.toString())
            ResourceChip("💎", gems.toString())
        }

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF5BB9F2)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = Color.White
            )
        }
    }
}

@Composable
fun LevelBadge(level: Int) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color(0xFF3A92F6)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$level",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
    }
}

@Composable
fun ResourceChip(icon: String, amount: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFFF7EFD9))
            .border(1.dp, Color(0xFFD1B07A), RoundedCornerShape(20.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon, fontSize = 14.sp)

        Spacer(modifier = Modifier.width(4.dp))

        Text(
            text = amount,
            color = Color(0xFF7A4E1D),
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}

@Composable
fun AdoptionCard(currentRequest: AdoptionRequest) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF6E7CC)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFE8D2AA)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "🧍", fontSize = 28.sp)
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${currentRequest.adopterName} wants:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF8A5B2D)
                )

                Text(
                    text = "${currentRequest.requestedTails}-Tailed ${currentRequest.requestedBreed}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color(0xFF6E451C)
                )

                Text(
                    text = "Merge matching dogs to create it!",
                    fontSize = 13.sp,
                    color = Color(0xFF9B6835)
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF8CE36C))
                    .padding(horizontal = 10.dp, vertical = 9.dp)
            ) {
                Text(
                    text = "$${currentRequest.reward}",
                    color = Color(0xFF4C6A12),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun DogHouseGrid(onSpawn: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        DogHouseButton("🏚️", "Beagle", onClick = { onSpawn("Beagle") })
        DogHouseButton("🏡", "German", onClick = { onSpawn("German") })
    }
}

@Composable
fun DogHouseButton(
    icon: String,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .width(130.dp)
                .height(54.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFFEFD8AF))
                .border(1.dp, Color(0xFFB9824A), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = icon, fontSize = 25.sp)
        }

        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF7A4E1D)
        )

        Text(
            text = "10⚡",
            fontSize = 10.sp,
            color = Color(0xFF9B6835)
        )
    }
}

@Composable
fun GameBoard(
    board: List<DogTile>,
    selectedIndex: Int?,
    onTileClicked: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(480.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFD8AF)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFFF5F1E8))
                .padding(8.dp)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(board) { index, tile ->
                    BoardTile(
                        tile = tile,
                        isSelected = selectedIndex == index,
                        onClick = { onTileClicked(index) }
                    )
                }
            }
        }
    }
}

@Composable
fun BoardTile(
    tile: DogTile,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = when {
        tile.locked -> Color(0xFFB78D5C)
        tile.tails == 0 -> Color(0xFFF2EEE5)
        tile.tails == 1 -> Color(0xFFFFD6C2)
        tile.tails == 2 -> Color(0xFFFFE2B8)
        tile.tails == 3 -> Color(0xFFD8F4D2)
        tile.tails == 4 -> Color(0xFFCAE9FF)
        else -> Color(0xFFF6D1F2)
    }

    val borderColor = when {
        isSelected -> Color(0xFF3A92F6)
        tile.locked -> Color(0xFF8C6841)
        tile.tails == 0 -> Color(0xFFD7D1C6)
        else -> Color(0xFFE3C38A)
    }

    val imageRes = remember(tile.breed, tile.tails) {
        getDogImage(tile.breed, tile.tails)
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(enabled = !tile.locked) {
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        when {
            tile.locked -> Text("🔒", fontSize = 20.sp)

            tile.tails == 0 -> Text("")

            else -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(id = imageRes),
                    contentDescription = tile.breed,
                    modifier = Modifier.size(45.dp),
                    contentScale = ContentScale.Fit
                )

                Text(
                    text = "${tile.tails} tail",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF7A4E1D)
                )
            }
        }
    }
}

@Composable
fun BottomMenu() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        MenuButton(icon = Icons.Default.ArrowBack, label = "Back")
        MenuButton(icon = Icons.Default.Home, label = "Shelter")
        MenuButton(icon = Icons.Default.List, label = "Dogs")
        MenuButton(icon = Icons.Default.ShoppingCart, label = "Shop")
        MenuButton(icon = Icons.Default.Settings, label = "Tasks")
    }
}

@Composable
fun MenuButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF55B7F3))
                .border(1.dp, Color(0xFF2E8FCC), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(25.dp)
            )
        }

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = label,
            fontSize = 10.sp,
            color = Color(0xFF7A4E1D)
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PawMergePreview() {
    PawMergeAppTheme {
        PawMergeScreen()
    }
}