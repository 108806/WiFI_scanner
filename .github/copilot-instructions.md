<!-- Use this file to provide workspace-specific custom instructions to Copilot. For more details, visit https://code.visualstudio.com/docs/copilot/copilot-customization#_use-a-githubcopilotinstructionsmd-file -->

# Android WiFi Scanner Project Instructions

This is an Android application project that scans for WiFi networks and exports detailed information to JSON files.

## Project Context

- **Target Device**: Samsung Galaxy A55 5G
- **Platform**: Android (Kotlin)
- **Minimum SDK**: API 24 (Android 7.0)
- **Target SDK**: API 34 (Android 14)

## Key Features

- WiFi network scanning with comprehensive data collection
- GPS location tracking for each detected network
- JSON export functionality with structured data format
- Real-time display of scan results
- Permission handling for location and storage access

## Development Guidelines

### Code Style
- Use Kotlin for all new code
- Follow Android Architecture Components patterns
- Use ViewBinding for layout inflation
- Implement proper error handling and user feedback

### Permissions
- Handle runtime permissions gracefully
- Provide clear explanations for permission requests
- Consider background location access for Android 10+

### Data Structure
- WiFi networks are represented by the `WifiNetwork` data class
- JSON export uses a structured format with metadata
- Location data includes latitude, longitude, altitude, and accuracy

### Testing
- Test primarily on Galaxy A55 5G connected via USB
- Verify GPS accuracy and signal strength measurements
- Test permission flows and edge cases

## Architecture

- **MVVM Pattern**: Using ViewModel and LiveData
- **Repository Pattern**: For data management
- **Service Component**: For background scanning capabilities

## Common Tasks

When working on this project:
- Ensure proper permission handling for location and WiFi access
- Maintain JSON export format consistency
- Test thoroughly on the target Galaxy A55 5G device
- Consider battery optimization and performance implications
- Follow Material Design guidelines for UI components
