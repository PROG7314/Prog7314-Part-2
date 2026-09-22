Aegis — Volunteer & Opportunity Matching Platform

Aegis is a native Android application designed to empower communities by connecting volunteers with meaningful social initiatives and non-profit opportunities. Built with modern Android architecture, offline-first principles, and modular design, Aegis provides intuitive opportunity matching, cloud synchronization, and volunteer hour tracking.

Tech Stack & PrerequisitesLanguage: KotlinUI 
Framework: XML Layouts with ViewBinding / Material Design 
ComponentsArchitecture: Clean Architecture / MVVM Pattern (View, ViewModel, Repository, Data)
Build System: Gradle (Kotlin DSL) with Version Catalog (libs.versions.toml)
Target SDK: Android 36 / Compile SDK 37  
Minimum SDK: API 25 (Android 7.1)
Agile/DevOps: GitHub Actions CI/CD Pipeline 
PrerequisitesAndroid Studio: Ladybug (or newer)
JDK: Java 17 (Temurin recommended)  
Android SDK Platform: API Level 37 installed via Android Studio SDK Manager 

Package Architecture

The codebase follows a modular clean architecture layer structure under com.example.aegis:  Plaintextcom.example.aegis/
├── core/                  # Base classes, lifecycle logging, shared utilities
│   ├── util/              # Pure Kotlin helper classes (e.g., InputValidator)
│   └── logging/           # AegisBaseActivity & AegisBaseFragment logging
├── data/                  # Data layer implementations
│   ├── local/             # UserPreferencesDataStore & Room local DB
│   ├── remote/            # REST API clients (Retrofit/Ktor)
│   └── repository/        # Repository implementations
├── domain/                # Core business models & repository interfaces
│   ├── model/             # Entity models
│   └── repository/        # Domain repository contracts
└── ui/                    # Presentation layer (XML Layouts & ViewModels)
    ├── auth/              # Login, Registration & Firebase SSO
    ├── main/              # Main volunteer dashboard
    ├── settings/          # User preferences & app settings
    └── custom/            # Interest & Opportunity matching feature

Getting Started

1. Clone the Repository
Bash git clone https:https://github.com/PROG7314/Prog7314-Part-2.git

cd Prog7314-Part-2

2. Gradle Sync
Open the project in Android Studio. 
Gradle will sync dependencies using gradle/libs.versions.toml. 
Ensure your local JAVA_HOME is set to JDK 17.  

3. Run the AppConnect a physical Android device (min SDK 25) or start an Android Emulator and execute:Bash./gradlew installDebug

Running Unit Tests
The test suite contains pure Kotlin JVM unit tests for core utilities and validation logic.  
To run unit tests via command line:  Bash./gradlew testDebugUnitTest
Reports are generated at:app/build/reports/tests/testDebugUnitTest/index.html

CI/CD Pipeline
Continuous Integration is managed via GitHub Actions (.github/workflows/android.yml).  

Pipeline Workflow:
Triggers: Automatically runs on pushes to main, develop, and feature/** branches, or PRs into main.  
Environment: Sets up Java 17 and caches Gradle dependencies for fast execution.
  
Automated Verification:
Executes all JVM unit tests (./gradlew testDebugUnitTest).  
Verifies project build integrity by assembling the debug APK (./gradlew assembleDebug).  

Team Responsibilities (PROG7314 POE Part 2)

Member 1: 

Core Architecture, Base Activity/Fragment Logging, Navigation Graph, and Interest Matching Logic.

Member 2: 

Authentication (Firebase SSO), Login/Register Screens, and Volunteer Hours Tracking.

Member 3: 

RESTful API Integration, Main Dashboard UI, and Opportunity Listings.

Member 4: DevOps, GitHub Actions CI/CD Pipeline, Unit Test Suite (InputValidatorTest), README, and Documentation. 

Physical Device Demonstration:
