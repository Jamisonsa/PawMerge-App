package com.example.pawmergeapp

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.room.*
import com.example.pawmergeapp.ui.theme.PawMergeAppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

// SJ - Represents one tile on the dog merge board.
data class DogTile(
    val tails: Int = 0,
    val breed: String = "",
    val locked: Boolean = false
)

// SJ - Represents one adoption request shown to the player.
data class AdoptionRequest(
    val adopterName: String,
    val requestedBreed: String,
    val requestedTails: Int,
    val reward: Int
)

// SJ - Stores saved player progress in the Room SQLite database.
@Entity(tableName = "player_progress")
data class PlayerProgress(
    @PrimaryKey val id: Int = 1,
    val coins: Int,
    val energy: Int,
    val gems: Int,
    val level: Int,
    val xp: Int,
    val boardData: String,
    val lastEnergyUpdateTime: Long
)

// SJ - Provides database methods for saving and loading progress.
@Dao
interface PlayerProgressDao {
    @Query("SELECT * FROM player_progress WHERE id = 1")
    suspend fun getProgress(): PlayerProgress?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: PlayerProgress)
}

// SJ - Defines the Room database for the app.
@Database(
    entities = [PlayerProgress::class],
    version = 2
)
abstract class PawMergeDatabase : RoomDatabase() {
    abstract fun playerProgressDao(): PlayerProgressDao
}

// SJ - Creates one shared database instance for the whole app.
object PawMergeDatabaseProvider {
    private var database: PawMergeDatabase? = null

    fun getDatabase(context: Context): PawMergeDatabase {
        return database ?: synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                PawMergeDatabase::class.java,
                "pawmerge_database"
            )
                .fallbackToDestructiveMigration(false)                .build()

            database = instance
            instance
        }
    }
}

// SJ - Creates the starting board with dogs, empty spaces, and locked spaces.
fun defaultBoard(): List<DogTile> {
    return listOf(
        DogTile(1, "Beagle"), DogTile(1, "Beagle"), DogTile(1, "German"), DogTile(1, "Golden"), DogTile(1, "German"),
        DogTile(), DogTile(1, "Beagle"), DogTile(1, "German"), DogTile(1, "Golden"), DogTile(1, "Beagle"),
        DogTile(locked = true), DogTile(1, "Golden"), DogTile(), DogTile(1, "German"), DogTile(1, "Beagle"),
        DogTile(1, "German"), DogTile(1, "Beagle"), DogTile(), DogTile(), DogTile(1, "Golden"),
        DogTile(), DogTile(locked = true), DogTile(1, "Beagle"), DogTile(), DogTile(),
        DogTile(1, "German"), DogTile(1, "German"), DogTile(locked = true), DogTile(1, "Golden"), DogTile(1, "Beagle")
    )
}

// SJ - Converts the board into a string so it can be saved in SQLite.
fun boardToString(board: List<DogTile>): String {
    return board.joinToString("|") { tile ->
        "${tile.tails},${tile.breed},${tile.locked}"
    }
}

// SJ - Converts the saved board string back into board tiles.
fun stringToBoard(boardData: String): List<DogTile> {
    return boardData.split("|").map { tileText ->
        val parts = tileText.split(",")

        DogTile(
            tails = parts.getOrNull(0)?.toIntOrNull() ?: 0,
            breed = parts.getOrNull(1) ?: "",
            locked = parts.getOrNull(2)?.toBooleanStrictOrNull() ?: false
        )
    }
}

// SJ - Defines all routes used by the bottom navigation.
sealed class Screen(val route: String, val label: String) {
    object Shelter : Screen("shelter", "Shelter")
    object Dogs : Screen("dogs", "Dogs")
    object Shop : Screen("shop", "Shop")
    object Tasks : Screen("tasks", "Tasks")
    object Settings : Screen("settings", "Settings")
}

// SJ - Returns the correct image based on dog breed and tail level.
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

        "Golden" -> when (tails) {
            1 -> R.drawable.golden_retriever_level_1
            2 -> R.drawable.golden_retriever_level_2
            3 -> R.drawable.golden_retriever_level_3
            4 -> R.drawable.golden_retriever_level_4
            5 -> R.drawable.golden_retriever_level_5
            else -> R.drawable.golden_retriever_level_1
        }

        else -> R.drawable.beagle_level_1
    }
}

// SJ - Creates a random adoption request for the player.
fun generateRandomRequest(): AdoptionRequest {
    val breeds = listOf("Beagle", "German", "Golden")
    val tails = listOf(2, 3, 4, 5).random()

    return AdoptionRequest(
        adopterName = listOf("Maya", "Chris", "Jordan", "Aaliyah", "Noah", "Sam").random(),
        requestedBreed = breeds.random(),
        requestedTails = tails,
        reward = tails * 125
    )
}

// SJ - Main Android activity that starts the Compose app.
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
                    PawMergeApp()
                }
            }
        }
    }
}

// SJ - Main app container with bottom navigation and screen routing.
@Composable
fun PawMergeApp() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            PawMergeBottomNavigation(navController)
        },
        containerColor = Color(0xFFF6E3BE)
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Shelter.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Shelter.route) { PawMergeScreen() }
            composable(Screen.Dogs.route) { DogsScreen() }
            composable(Screen.Shop.route) { ShopScreen() }
            composable(Screen.Tasks.route) { TasksScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}

// SJ - Main gameplay screen where players spawn, merge, adopt, and unlock dogs.
@Composable
fun PawMergeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember {
        PawMergeDatabaseProvider.getDatabase(context)
    }

    var coins by remember { mutableStateOf(100) }
    var energy by remember { mutableStateOf(100) }
    var gems by remember { mutableStateOf(25) }
    var level by remember { mutableStateOf(1) }
    var xp by remember { mutableStateOf(0) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var currentRequest by remember { mutableStateOf(generateRandomRequest()) }
    var message by remember { mutableStateOf("Merge matching dogs to add tails!") }
    var showEnergyDialog by remember { mutableStateOf(false) }
    var showBoardFullDialog by remember { mutableStateOf(false) }
    var showUnlockDialog by remember { mutableStateOf(false) }
    var lockedTileIndex by remember { mutableStateOf<Int?>(null) }
    var showNotEnoughCoinsDialog by remember { mutableStateOf(false) }
    val board = remember {
        mutableStateListOf<DogTile>().apply {
            addAll(defaultBoard())
        }
    }

    // SJ - Saves player stats and board layout into Room.
    fun saveProgress() {
        scope.launch {
            database.playerProgressDao().saveProgress(
                PlayerProgress(
                    coins = coins,
                    energy = energy,
                    gems = gems,
                    level = level,
                    xp = xp,
                    lastEnergyUpdateTime = System.currentTimeMillis(),
                    boardData = boardToString(board)
                )
            )
        }
    }

    // SJ - Loads saved progress and restores energy based on time away.
    LaunchedEffect(Unit) {
        val savedProgress = database.playerProgressDao().getProgress()

        if (savedProgress != null) {
            val now = System.currentTimeMillis()
            val elapsedMillis = now - savedProgress.lastEnergyUpdateTime
            val fiveMinutesMillis = 5 * 60 * 1000L
            val energyGained = ((elapsedMillis / fiveMinutesMillis) * 10).toInt()

            coins = savedProgress.coins
            gems = savedProgress.gems
            level = savedProgress.level
            xp = savedProgress.xp
            energy = (savedProgress.energy + energyGained).coerceAtMost(100)

            board.clear()
            board.addAll(stringToBoard(savedProgress.boardData))

            saveProgress()
        }
    }

    // SJ - Regenerates energy every 5 minutes while the app is open.
    LaunchedEffect(Unit) {
        while (true) {
            delay(300000L)

            if (energy < 100) {
                energy = (energy + 10).coerceAtMost(100)
                message = "Energy regenerated by 10!"
                saveProgress()
            }
        }
    }

    // SJ - Checks if XP is high enough to increase the player level.
    fun checkLevelUp() {
        if (xp >= level * 50) {
            xp = 0
            level++
            message = "Level Up! You reached Level $level!"
        }
    }

    // SJ - Rewards the player if a merged dog matches the adoption request.
    fun checkAdoption(index: Int) {
        val dog = board[index]

        if (dog.breed == currentRequest.requestedBreed && dog.tails == currentRequest.requestedTails) {
            coins += currentRequest.reward
            xp += 25
            checkLevelUp()
            board[index] = DogTile()
            message = "${currentRequest.adopterName} adopted a ${currentRequest.requestedTails}-tailed ${currentRequest.requestedBreed}! +$${currentRequest.reward}"
            currentRequest = generateRandomRequest()
            saveProgress()
        }
    }

    // SJ - Handles tile clicks for selecting, merging, and locked tile prompts.
    fun onTileClicked(index: Int) {
        val clickedDog = board[index]

        if (clickedDog.locked) {
            lockedTileIndex = index
            showUnlockDialog = true
            return
        }

        if (clickedDog.tails == 0) return

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
                xp += 10
                checkLevelUp()

                if (!message.startsWith("Level Up")) {
                    message = "Merged into a ${newTailCount}-tailed ${dogOne.breed}! +$${newTailCount * 10}"
                }

                checkAdoption(first)
                saveProgress()
            } else {
                message = "Dogs must be the same breed and have the same tail count."
            }

            selectedIndex = null
        }
    }

    // SJ - Spawns a new level-one dog if energy and board space are available.
    fun spawnDogFromHouse(breed: String) {
        val energyCost = 10

        if (energy < energyCost) {
            showEnergyDialog = true
            message = "Not enough energy!"
            return
        }

        val emptyIndex = board.indexOfFirst { it.tails == 0 && !it.locked }

        if (emptyIndex == -1) {
            showBoardFullDialog = true
            message = "No empty spaces on the board!"
            return
        }

        energy -= energyCost
        board[emptyIndex] = DogTile(tails = 1, breed = breed)
        message = "Spawned a 1-tailed $breed!"
        saveProgress()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6E3BE))
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
            .padding(bottom = 20.dp)
    ) {
        TopStatusBar(level, xp, energy, coins, gems)

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

    // SJ - Shows warning if player tries to spawn without enough energy.
    if (showEnergyDialog) {
        AlertDialog(
            onDismissRequest = { showEnergyDialog = false },
            title = { Text("Out of Energy") },
            text = {
                Text("You do not have enough energy to spawn another dog. Wait for more energy or buy upgrades in the shop.")
            },
            confirmButton = {
                Button(onClick = { showEnergyDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    // SJ - Shows warning if no empty board spaces are available.
    if (showBoardFullDialog) {
        AlertDialog(
            onDismissRequest = { showBoardFullDialog = false },
            title = { Text("Board Full") },
            text = {
                Text("There are no empty spaces left. Merge dogs together to make room before spawning another dog.")
            },
            confirmButton = {
                Button(onClick = { showBoardFullDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    // SJ - Lets the player unlock locked board spaces with coins.
    if (showUnlockDialog) {
        AlertDialog(
            onDismissRequest = {
                showUnlockDialog = false
                lockedTileIndex = null
            },
            title = { Text("Unlock Tile?") },
            text = { Text("Unlock this board space for 100 coins?") },
            confirmButton = {
                Button(
                    onClick = {
                        val index = lockedTileIndex

                        if (index != null && coins >= 100) {
                            coins -= 100
                            board[index] = DogTile()
                            message = "Tile unlocked! -100 coins"
                            saveProgress()
                        } else {
                            showNotEnoughCoinsDialog = true
                        }

                        showUnlockDialog = false
                        lockedTileIndex = null
                    }
                ) {
                    Text("Unlock")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showUnlockDialog = false
                        lockedTileIndex = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
    if (showNotEnoughCoinsDialog) {
        AlertDialog(
            onDismissRequest = {
                showNotEnoughCoinsDialog = false
            },
            title = {
                Text("Not Enough Coins")
            },
            text = {
                Text("You do not have enough coins to unlock this tile. Merge more dogs and complete adoptions to earn coins.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNotEnoughCoinsDialog = false
                    }
                ) {
                    Text("OK")
                }
            }
        )
    }
}

// SJ - Displays the Material bottom navigation bar.
@Composable
fun PawMergeBottomNavigation(navController: NavController) {
    val screens = listOf(
        Screen.Shelter,
        Screen.Dogs,
        Screen.Shop,
        Screen.Tasks,
        Screen.Settings
    )

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    NavigationBar(containerColor = Color(0xFFF6E7CC)) {
        screens.forEach { screen ->
            NavigationBarItem(
                selected = currentRoute == screen.route,
                onClick = {
                    navController.navigate(screen.route) {
                        popUpTo(Screen.Shelter.route)
                        launchSingleTop = true
                    }
                },
                icon = {
                    when (screen) {
                        Screen.Shelter -> Icon(Icons.Default.Home, contentDescription = screen.label)
                        Screen.Dogs -> Icon(Icons.Default.List, contentDescription = screen.label)
                        Screen.Shop -> Icon(Icons.Default.ShoppingCart, contentDescription = screen.label)
                        Screen.Tasks -> Icon(Icons.Default.Settings, contentDescription = screen.label)
                        Screen.Settings -> Icon(Icons.Default.Settings, contentDescription = screen.label)
                    }
                },
                label = { Text(screen.label) }
            )
        }
    }
}

// SJ - Creates and displays a local Android notification.
fun showPawMergeNotification(context: Context) {
    val channelId = "pawmerge_channel"

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            channelId,
            "PawMerge Notifications",
            NotificationManager.IMPORTANCE_DEFAULT
        )

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("PawMerge Energy Reminder")
        .setContentText("Your dogs are ready! Come back and keep merging.")
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()

    if (
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    ) {
        NotificationManagerCompat.from(context).notify(1, notification)
    }
}

// SJ - Represents one dog fact returned from the API.
data class DogFactItem(
    val attributes: DogFactAttributes
)

// SJ - Stores the dog fact text from the API response.
data class DogFactAttributes(
    val body: String
)

// SJ - Represents the full dog fact API response.
data class DogFactResponse(
    val data: List<DogFactItem>
)

// SJ - Retrofit service for requesting dog facts from the internet.
interface DogFactApiService {
    @GET("api/v2/facts")
    suspend fun getDogFacts(): DogFactResponse
}

// SJ - Retrofit instance used by the Tasks screen.
val dogFactApi: DogFactApiService = Retrofit.Builder()
    .baseUrl("https://dogapi.dog/")
    .addConverterFactory(GsonConverterFactory.create())
    .build()
    .create(DogFactApiService::class.java)

// SJ - Displays player level, XP, energy, coins, and gems.
@Composable
fun TopStatusBar(
    level: Int,
    xp: Int,
    energy: Int,
    coins: Int,
    gems: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LevelBadge(level = level)

            Text(
                text = "$xp XP",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF7A4E1D)
            )
        }

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

// SJ - Displays the circular player level badge.
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

// SJ - Displays one resource chip for energy, coins, or gems.
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

// SJ - Displays the current adoption request and reward amount.
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

// SJ - Displays all dog house buttons used to spawn new dogs.
@Composable
fun DogHouseGrid(onSpawn: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        DogHouseButton("🏚️", "Beagle", onClick = { onSpawn("Beagle") })
        DogHouseButton("🏡", "German", onClick = { onSpawn("German") })
        DogHouseButton("🏠", "Golden", onClick = { onSpawn("Golden") })
    }
}

// SJ - Displays one dog house spawn button.
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
                .width(95.dp)
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

// SJ - Displays the full dog merge board using a grid.
@Composable
fun GameBoard(
    board: List<DogTile>,
    selectedIndex: Int?,
    onTileClicked: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(560.dp),
        shape = RoundedCornerShape(28.dp),
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
                verticalArrangement = Arrangement.spacedBy(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = false
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

// SJ - Displays one tile on the game board.
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
            .clickable {
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
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF7A4E1D)
                )
            }
        }
    }
}

// SJ - Displays the collection of all dog breeds and tail levels.
@Composable
fun DogsScreen() {
    val dogCollection = listOf(
        DogTile(1, "Beagle"),
        DogTile(2, "Beagle"),
        DogTile(3, "Beagle"),
        DogTile(4, "Beagle"),
        DogTile(5, "Beagle"),
        DogTile(1, "German"),
        DogTile(2, "German"),
        DogTile(3, "German"),
        DogTile(4, "German"),
        DogTile(5, "German"),
        DogTile(1, "Golden"),
        DogTile(2, "Golden"),
        DogTile(3, "Golden"),
        DogTile(4, "Golden"),
        DogTile(5, "Golden")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6E3BE))
            .padding(16.dp)
    ) {
        Text(
            text = "Dog Collection",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF6E451C)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "View all collectible dogs and merge levels.",
            fontSize = 15.sp,
            color = Color(0xFF8A5B2D)
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(dogCollection) { _, dog ->
                CollectionDogCard(dog)
            }
        }
    }
}

// SJ - Displays one dog card in the collection screen.
@Composable
fun CollectionDogCard(dog: DogTile) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF6E7CC)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = getDogImage(dog.breed, dog.tails)),
                contentDescription = "${dog.breed} ${dog.tails}",
                modifier = Modifier.size(95.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = dog.breed,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF6E451C)
            )

            Text(
                text = "Level ${dog.tails}",
                fontSize = 14.sp,
                color = Color(0xFF8A5B2D)
            )

            Text(
                text = "${dog.tails} tail(s)",
                fontSize = 13.sp,
                color = Color(0xFF9B6835)
            )
        }
    }
}

// SJ - Shop screen where players spend coins on boosts.
@Composable
fun ShopScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember {
        PawMergeDatabaseProvider.getDatabase(context)
    }

    var shopMessage by remember { mutableStateOf("Spend coins to buy useful game boosts.") }
    var coins by remember { mutableStateOf(0) }
    var energy by remember { mutableStateOf(0) }
    var gems by remember { mutableStateOf(0) }
    var level by remember { mutableStateOf(1) }
    var xp by remember { mutableStateOf(0) }
    var boardData by remember { mutableStateOf(boardToString(defaultBoard())) }
    var showNotEnoughCoinsDialog by remember { mutableStateOf(false) }

    // SJ - Loads the current saved progress so the shop can update resources.
    LaunchedEffect(Unit) {
        val progress = database.playerProgressDao().getProgress()

        if (progress != null) {
            coins = progress.coins
            energy = progress.energy
            gems = progress.gems
            level = progress.level
            xp = progress.xp
            boardData = progress.boardData
        } else {
            coins = 100
            energy = 100
            gems = 25
        }
    }

    // SJ - Saves purchases back into the Room database.
    fun saveShopProgress() {
        scope.launch {
            database.playerProgressDao().saveProgress(
                PlayerProgress(
                    coins = coins,
                    energy = energy,
                    gems = gems,
                    level = level,
                    xp = xp,
                    boardData = boardData,
                    lastEnergyUpdateTime = System.currentTimeMillis()
                )
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6E3BE))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Shop",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF6E451C)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Coins: $coins | Energy: $energy | Gems: $gems",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF7A4E1D)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = shopMessage,
            fontSize = 14.sp,
            color = Color(0xFF8A5B2D)
        )

        Spacer(modifier = Modifier.height(20.dp))

        ShopItemCard(
            title = "Energy Refill",
            description = "Buy +50 energy for 100 coins.",
            buttonText = "Buy for 100 Coins",
            onBuy = {
                if (coins >= 100) {
                    coins -= 100
                    energy = (energy + 50).coerceAtMost(100)
                    shopMessage = "Energy refilled! +50 energy."
                    saveShopProgress()
                } else {
                    showNotEnoughCoinsDialog = true
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        ShopItemCard(
            title = "Gem Pack",
            description = "Buy +10 gems for 250 coins.",
            buttonText = "Buy for 250 Coins",
            onBuy = {
                if (coins >= 250) {
                    coins -= 250
                    gems += 10
                    shopMessage = "Gem pack purchased! +10 gems."
                    saveShopProgress()
                } else {
                    showNotEnoughCoinsDialog = true
                }
            }
        )
    }
    if (showNotEnoughCoinsDialog) {
        AlertDialog(
            onDismissRequest = {
                showNotEnoughCoinsDialog = false
            },
            title = {
                Text("Not Enough Coins")
            },
            text = {
                Text("You do not have enough coins to purchase this item. Complete more merges and adoptions to earn coins.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNotEnoughCoinsDialog = false
                    }
                ) {
                    Text("OK")
                }
            }
        )
    }
}

// SJ - Reusable card for each shop item.
@Composable
fun ShopItemCard(
    title: String,
    description: String,
    buttonText: String,
    onBuy: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF6E7CC)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF6E451C)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = description,
                fontSize = 14.sp,
                color = Color(0xFF8A5B2D)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(onClick = onBuy) {
                Text(buttonText)
            }
        }
    }
}

// SJ - Daily tasks screen with API dog facts and notifications.
@Composable
fun TasksScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var dogTip by remember {
        mutableStateOf("Tap the button to load a daily dog care tip from the internet.")
    }

    val tasks = listOf(
        "Spawn 3 dogs",
        "Merge 2 dogs",
        "Complete 1 adoption request",
        "Load a daily dog tip",
        "Send an energy reminder"
    )

    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                showPawMergeNotification(context)
            }
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6E3BE))
            .padding(20.dp)
    ) {
        Text(
            text = "Daily Tasks",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF6E451C)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Complete daily tasks and learn dog care tips.",
            fontSize = 15.sp,
            color = Color(0xFF8A5B2D)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF7EFD9))
        ) {
            Text(
                text = dogTip,
                modifier = Modifier.padding(16.dp),
                fontSize = 14.sp,
                color = Color(0xFF7A4E1D)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                scope.launch {
                    try {
                        dogTip = "Loading dog care tip..."
                        val response = dogFactApi.getDogFacts()
                        dogTip = response.data.firstOrNull()?.attributes?.body
                            ?: "No dog tip found."
                    } catch (e: Exception) {
                        dogTip = "Could not load dog tip. Check your internet connection."
                    }
                }
            }
        ) {
            Text("Get Daily Dog Tip")
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        showPawMergeNotification(context)
                    } else {
                        notificationPermissionLauncher.launch(
                            Manifest.permission.POST_NOTIFICATIONS
                        )
                    }
                } else {
                    showPawMergeNotification(context)
                }
            }
        ) {
            Text("Send Energy Reminder")
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Task Checklist",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF6E451C)
        )

        Spacer(modifier = Modifier.height(10.dp))

        tasks.forEach { task ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF6E7CC))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "🐾", fontSize = 18.sp)

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = task,
                        fontSize = 14.sp,
                        color = Color(0xFF7A4E1D),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// SJ - Resets saved progress back to the starting game state.
suspend fun resetSavedProgress(context: Context) {
    val database = PawMergeDatabaseProvider.getDatabase(context)
    database.playerProgressDao().saveProgress(
        PlayerProgress(
            coins = 100,
            energy = 100,
            gems = 25,
            level = 1,
            xp = 0,
            lastEnergyUpdateTime = System.currentTimeMillis(),
            boardData = boardToString(defaultBoard())
        )
    )
}

// SJ - Settings screen with share intent and reset progress dialog.
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showResetDialog by remember { mutableStateOf(false) }
    var resetMessage by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6E3BE))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF6E7CC)),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Settings",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6E451C)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Manage app settings and share your progress.",
                    fontSize = 15.sp,
                    color = Color(0xFF8A5B2D)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "I am playing PawMerge! Merge dogs, complete adoption requests, and earn rewards!"
                            )
                        }

                        context.startActivity(
                            Intent.createChooser(shareIntent, "Share PawMerge")
                        )
                    }
                ) {
                    Text("Share PawMerge")
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        showResetDialog = true
                    }
                ) {
                    Text("Reset Saved Progress")
                }

                if (resetMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = resetMessage,
                        fontSize = 13.sp,
                        color = Color(0xFF7A4E1D),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = {
                showResetDialog = false
            },
            title = {
                Text("Reset Progress?")
            },
            text = {
                Text("This will reset your saved coins, energy, gems, XP, level, and dog board. Restart the app or return to Shelter to see the reset board.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            resetSavedProgress(context)
                            resetMessage = "Progress reset successfully."
                            showResetDialog = false
                        }
                    }
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showResetDialog = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

// SJ - Small preview for Android Studio design tools.
@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PawMergePreview() {
    PawMergeAppTheme {
        PawMergeApp()
    }
}