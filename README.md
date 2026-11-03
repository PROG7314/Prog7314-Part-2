Aegis — Volunteer & Opportunity Matching Platform
Final POE

GitHub Link:
Demonstration video link: 

STATUS BADGE
[![Aegis Android CI Build & Test](https://github.com/PROG7314/Prog7314-Part-2/actions/workflows/android.yml/badge.svg?branch=main)](https://github.com/PROG7314/Prog7314-Part-2/actions/workflows/android.yml)

===============================================================================
- Project Overview & Target Audience
Aegis is a native Android application designed to empower communities by seamlessly connecting volunteers with meaningful social initiatives, non-profit organizations, and community service opportunities. Built with modern Android architecture, offline-first synchronization, and real-time Firebase Cloud Messaging (FCM) notifications, Aegis provides robust opportunity matching, multi-language localization, and secure volunteer hour tracking.

Target Audience: 
Community volunteers seeking verified social impact opportunities, non-profit organizations managing local initiatives, and operational managers coordinating community engagement.

===============================================================================
Tech Stack & Prerequisites

Language: Kotlin

UI Framework: XML Layouts with ViewBinding / Material Design Components

Architecture: Clean Architecture / MVVM Pattern (View, ViewModel, Repository, Data)

Build System: Gradle (Kotlin DSL) with Version Catalog (libs.versions.toml)

Target SDK: Android 36 / Compile SDK 37

Minimum SDK: API 25 (Android 7.1)

Backend & Services: Node.js Express REST API, Firebase Authentication & Firestore, Firebase Cloud Messaging (FCM)

DevOps: GitHub Actions CI/CD Pipeline

Prerequisites: Android Studio Ladybug (or newer), JDK 17 (Temurin recommended)
===============================================================================
Package Architecture

The codebase follows a modular clean architecture layer structure under com.example.aegis
app/

├── manifests/
│   └── AndroidManifest.xml
├── kotlin+java/
│   └── com.example.aegis/
│       ├── core/                    # Core utilities, logging & base classes
│       ├── data/                    # Repositories, local RoomDB, and remote clients
│       ├── domain/                  # Domain entity models & repository contracts
│       ├── notifications/           # FCM messaging service and notification routers
│       ├── ui/                      # Presentation layer (Activities, Fragments, ViewModels)
│       ├── AegisApp.kt              # Application class
│       └── MainActivity.kt          # Root Activity hosting navigation graph
├── com.example.aegis (androidTest)/ # Instrumented UI tests
│   └── ExampleInstrumentedTest.kt
└── com.example.aegis (test)/        # Local JVM Unit Tests
    ├── core.util/
    │   └── InputValidatorTest.kt    # Input validation & utility unit tests
    ├── data.local/
    │   └── OfflineCacheTest.kt      # Offline sync & caching unit tests
    └── ui.listing/
        └── ...                      # Feature-specific listing unit tests
===============================================================================
System Architecture Diagram

The system integrates client mobile architecture with a secure backend and cloud infrastructure:

[ Android App (Kotlin / MVVM) ]
         │                ▲
         │ (Retrofit REST │ (FCM Push
         ▼                │  Notifications)
  [ Node.js Express API ] ─── [ Firebase Cloud Messaging ]
         │
         ├──► [ Firebase Authentication / SSO ]
         └──► [ Firestore / RoomDB (Offline Sync) ]

===============================================================================
Getting Started & Fresh Clone Setup

To set up and run the application locally on a fresh clone:

Clone the Repository

git clone https://github.com/PROG7314/Prog7314-Part-2.git
cd Prog7314-Part-2


2. Gradle Sync

Open the project in Android Studio.

Ensure your local JAVA_HOME is configured to JDK 17.

Gradle will automatically sync dependencies using gradle/libs.versions.toml.

3. Run the Application

Connect a physical Android device (min SDK 25) or start an Android Emulator

Execute via terminal:./gradlew installDebug

===============================================================================
Automated Testing & CI Pipeline

Continuous Integration is managed via GitHub Actions (.github/workflows/android.yml), automatically ensuring build integrity and code quality on every push and pull request

Triggers: Automatically runs on pushes to main, develop, and feature/** / feat/** branches, and PRs into main

Environment: Configures Java 17 and caches Gradle and npm dependencies for optimal execution speed

Automated Verification: Executes all backend unit tests (npm test) and Android JVM unit tests (./gradlew testDebugUnitTest), and verifies build integrity by assembling the debug APK (./gradlew assembleDebug)

To run unit tests locally via command line:

Android Unit Tests: ./gradlew testDebugUnitTest (Reports generated at app/build/reports/tests/testDebugUnitTest/index.html)

Backend Unit Tests: cd backend && npm test

===============================================================================
Core Features Breakdown

Secure Authentication & Biometrics: Supports Firebase SSO, email/password sign-in with recovery options, and device biometric authentication integration (androidx.biometric)

Opportunity Listings & Feed: Browse social initiatives filtered dynamically by cause categories and skills, supporting offline read/write caching via RoomDB

Volunteer Hour Tracking: Track commitments, check-ins, and completed hours verified by partner organizations

Real-Time Notifications: Push notifications triggered by real backend events using Firebase Cloud Messaging (FCM) Admin SDK

Multi-Language Localization: Full UI localization supporting English and official South African languages with dynamic locale switching

===============================================================================
Team Responsibilities (PROG7314 PoE Final)

Member 1: Melo (30 Marks)   

Core Files & Scope: Core app shell, navigation, Settings screen, and Feature 1.   

Primary Rubric & Bug Fixes: Multi-language localization (including Afrikaans and Zulu support, locale configuration, and dynamic language switching), UI consistency and theme integration (resolving hardcoded color clashes with Material theme), User-Defined Feature 4, and fixing the Settings screen dark-mode persistence and API synchronization bug.

Member 2: Yaseera (25 Marks)

Core Files & Scope: Authentication (Firebase SSO, Splash screen), Login/Register screens, and Feature 2.   

Primary Rubric & Bug Fixes: Biometric authentication (BiometricPrompt and DataStore preferences), crash-free mobile device launch investigation, User-Defined Feature 5, and SSO bug remediation (replacing deprecated GoogleSignIn with Credential Manager, fixing null pointer crashes, removing raw exception text, adding email/password sign-in with password recovery, and fixing onboarding save issues).

Member 3: Michaela (20 Marks)   

Core Files & Scope: RESTful API backend, data layer, Feature 3, Volunteer Home, Org Home, Commitments, and Hours screens.   

Primary Rubric & Bug Fixes: Offline mode implementation (RoomDB, WorkManager, periodic backup, and /sync endpoint integration), real-time Firebase Cloud Messaging (FCM) notifications, resolving backend filtering bugs (listings.js AND/OR logic), reconciling seed data (devTools.js), fixing form validation defaults, building organization management and hour-tracking interfaces, and removing temporary test token fallbacks. 

Member 4: Demira (25 Marks)   

Core Files & Scope: DevOps, GitHub workflow management, CI/CD pipeline configuration, and project documentation.   

Primary Rubric & Bug Fixes: GitHub repository management (enforcing 75+ meaningful commits and PR workflows), maintaining a green automated CI/CD pipeline (android.yml running both backend and Android test suites), creating the 6+ required Android unit test suite (AegisValidationTest), Play Store packaging and signed AAB/APK release generation, store-listing/ asset preparation, drafting the AI-use write-up, and coordinating the final demonstration video.
===============================================================================
Release Notes & Play Store Readiness

Version Tag: v1.0.0-final-poe

Binaries: Signed Android App Bundle (.aeb/.aab) and APK binaries are attached to the official GitHub Release

Store Listing Assets: Located in the repository under store-listing/, containing launcher icons, feature graphics, promotional screenshots, and privacy policy documentation
===============================================================================










