# Flashbang Prank App

Feetbang is a simple Android application built with Jetpack Compose designed for a classic "flashbang" prank. 

## What it Does

When the app is opened, it displays an initial image. As soon as a user clicks anywhere on the screen, the "flashbang" is triggered:
- **Maximum Volume**: The app instantly sets the system media volume to its maximum level and plays a flashbang sound effect.
- **Visual Flash**: The screen brightness is immediately set to 100%, and a solid white overlay covers the entire screen.
- **The Fade**: Over the course of 3 seconds, the white flash slowly fades away, and the screen brightness returns to a normal level.
- **The Result**: Once the flash has cleared, a new "after" image is revealed.

## Prerequisites

To function correctly, the app requires the `WRITE_SETTINGS` permission to programmatically control the system screen brightness. The app will automatically prompt you to grant this permission in your system settings on the first launch.

## Getting Started

The easiest way to get this project running is to **git clone** this repository directly into **Android Studio**. 

Android Studio will handle the environment setup, dependency resolution, and deployment to your device or emulator.
