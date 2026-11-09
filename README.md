# GORILLA AI

GORILLA AI is an Android chat-interface prototype for a personal AI assistant.

## Current prototype
- ChatGPT-inspired dark chat screen with a GORILLA AI identity.
- Welcome screen with quick prompts for homework, image creation, style advice and conversation.
- Message composer with send action and local chat bubbles.
- Attachment chooser for gallery images, files and camera intent.
- Image preview inside the conversation.
- Navigation and settings dialogs.

## Important status
This commit implements the Android interface prototype only. A real AI model, image-generation provider, internet search, user registration, cloud chat history, unique account IDs and the owner/admin console are **not connected yet**. The current assistant reply is a local placeholder, not an AI-generated answer.

## Build
Open the repository in Android Studio with JDK 17, sync Gradle, then choose **Build > Build APK(s)**.

The project uses Android Gradle Plugin 8.7.3, Kotlin 2.0.21, compile SDK 35, target SDK 35 and minimum SDK 23.
