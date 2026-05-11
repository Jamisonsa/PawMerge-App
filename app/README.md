# PawMerge

## Project Overview
PawMerge is a Jetpack Compose Android game where players spawn dogs, merge matching dogs, complete adoption requests, earn coins, and build a collection of multi-tailed dogs. The goal of the app is to create a fun pet-themed merge game with progression, rewards, saved progress, and multiple interactive screens.

## App Features
- Custom app theme and UI
- Custom dog graphics and app icon
- Bottom navigation with multiple screens
- Dog merge gameplay
- Adoption request system
- Coin, gem, energy, XP, and level system
- Locked tiles that can be unlocked with coins
- Shop screen for buying energy and gems
- Dog collection screen
- Daily tasks screen
- Dog care tip REST API
- Local notifications
- Share intent
- SQLite/Room database saving
- Saved board progress
- Energy regeneration over time
- Dialogs for warnings and confirmations

## Screens
- **Shelter:** Main game board where dogs are spawned and merged.
- **Dogs:** Collection screen showing all dog breeds and tail levels.
- **Shop:** Allows players to buy energy and gems using coins.
- **Tasks:** Shows daily tasks, dog facts from an API, and notification reminders.
- **Settings:** Allows users to share the app and reset saved progress.

## Technologies Used
- Kotlin
- Jetpack Compose
- Material 3
- Navigation Compose
- Room Database / SQLite
- Retrofit
- Gson Converter
- Android Notifications
- Android Intents
- LazyVerticalGrid
- Coroutines

## Database Usage
The app uses Room/SQLite to save:
- Coins
- Energy
- Gems
- XP
- Player level
- Full board layout
- Last energy update time

This allows the player’s progress to remain saved after closing and reopening the app.

## REST API Usage
The app uses a dog facts API to load daily dog care tips on the Tasks screen.

API:
```text
https://dogapi.dog/api/v2/facts
```
## Intents
The Settings screen uses an Android share intent so users can share PawMerge with others.

## Notifications
The Tasks screen includes a notification feature that reminds the player to return to the game and continue merging dogs.

## Third-Party Libraries / Resources
- Android Jetpack Compose
- AndroidX Navigation Compose
- Room Database
- Retrofit
- Gson Converter
- Dog API
- Custom dog images created for the project

## How to Run
1. Open the project in Android Studio.
2. Sync Gradle files.
3. Build the project.
4. Run the app on an emulator or Android device.

## Author
Sanaa Jamison

## Course
Final Project Android App