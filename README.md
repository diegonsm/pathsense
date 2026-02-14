# PathSense

An accessible Android navigation app for visually impaired users. PathSense uses computer vision and machine learning to help users understand their surroundings through audio and haptic feedback.

## Features

### Three Operating Modes

#### Explore Mode
- **Object Detection**: Identifies objects in the camera view using MobileNet-SSD
- **Spatial Audio**: Announces objects using clock orientation (12 o'clock = straight ahead)
- **Proximity Alerts**: Distinguishes between near, medium, and far objects
- **Example announcement**: "Person at 12 o'clock, very close"

#### Text Mode
- **OCR Recognition**: Reads text from signs, documents, and screens using ML Kit
- **Auto-Read**: Automatically announces detected text
- **Text Display**: Shows detected text for sighted companions
- **Copy Function**: Copy detected text to clipboard

#### Navigate Mode
- **Depth Estimation**: Uses Depth Anything v2 for obstacle detection
- **Zone Alerts**: Divides view into 9 zones with proximity warnings
- **Path Guidance**: Announces "Clear path ahead" or obstacle locations
- **Safety Haptics**: Strong vibration for close obstacles

### Accessibility Features

- **TTS Audio Feedback**: Priority-based announcement queue with debouncing
- **Haptic Patterns**: Distinct vibration patterns for different events
- **High Contrast Mode**: Yellow text on black background
- **Large Text Mode**: 50% larger typography
- **TalkBack Integration**: Full semantic labels for screen readers
- **48dp+ Touch Targets**: WCAG-compliant button sizes

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        MainActivity                          │
├─────────────────────────────────────────────────────────────┤
│  PathSenseApp                                                │
│  ├── AccessibilityPreferences (DataStore)                   │
│  ├── AudioFeedbackManager (TTS)                             │
│  ├── HapticFeedbackManager (Vibration)                      │
│  └── NavHost                                                 │
│      ├── MainScreen                                          │
│      │   ├── ModeSelector (Explore | Text | Navigate)       │
│      │   └── Mode-specific screens                           │
│      └── SettingsScreen                                      │
├─────────────────────────────────────────────────────────────┤
│  PipelineCoordinator                                         │
│  ├── OcrPipeline (ML Kit)                                    │
│  ├── MobileNetSsdRunner (ONNX Runtime)                      │
│  └── DepthAnythingRunner (ONNX Runtime)                     │
├─────────────────────────────────────────────────────────────┤
│  CameraStreamer (CameraX)                                    │
│  └── FrameHub (Channels)                                     │
└─────────────────────────────────────────────────────────────┘
```

## Clock Orientation System

PathSense uses clock positions for spatial descriptions, inspired by Google Lookout:

```
        11  12  1
     10          2
      9    *    3
```

| Screen Position | Clock Position |
|-----------------|----------------|
| 0.0 - 0.17      | 9 o'clock (far left) |
| 0.17 - 0.33    | 10 o'clock |
| 0.33 - 0.50    | 11 o'clock |
| 0.50           | 12 o'clock (center) |
| 0.50 - 0.67    | 1 o'clock |
| 0.67 - 0.83    | 2 o'clock |
| 0.83 - 1.0     | 3 o'clock (far right) |

## Proximity Classification

Based on depth map closeness values (0-255, higher = closer):

| Closeness | Proximity | Description |
|-----------|-----------|-------------|
| >= 200    | NEAR      | ~1 meter or less |
| >= 100    | MED       | ~2-3 meters |
| >= 30     | FAR       | >4 meters |

## Project Structure

```
app/src/main/java/com/example/pathsense/
├── MainActivity.kt                 # Entry point, navigation
├── accessibility/
│   ├── AccessibilityPreferences.kt # DataStore settings
│   ├── AudioFeedbackManager.kt     # TTS with priority queue
│   ├── HapticFeedbackManager.kt    # Vibration patterns
│   └── SpatialDescriber.kt         # Clock orientation logic
├── camera/
│   └── CameraStreamer.kt           # CameraX frame capture
├── core/
│   ├── AssetUtils.kt               # ONNX model loading
│   ├── FrameData.kt                # Frame container
│   └── FrameHub.kt                 # Pipeline distribution
├── pipelines/
│   ├── PipelineCoordinator.kt      # ML pipeline orchestration
│   ├── depth/
│   │   ├── DepthAnythingRunner.kt  # Depth estimation
│   │   └── DepthSampler.kt         # Depth map sampling
│   ├── detection/
│   │   ├── CocoLabels.kt           # COCO class names
│   │   └── MobileNetSsdRunner.kt   # Object detection
│   ├── ocr/
│   │   └── OcrPipeline.kt          # Text recognition
│   └── results/
│       └── results.kt              # Data classes
└── ui/
    ├── components/
    │   ├── AccessibleButton.kt     # 48dp+ touch targets
    │   ├── CameraViewWithOverlay.kt # Camera + detections
    │   ├── FeedbackIndicator.kt    # TTS visual indicator
    │   └── ModeSelector.kt         # Bottom navigation
    ├── screens/
    │   ├── ExploreScreen.kt        # Object detection mode
    │   ├── MainScreen.kt           # Mode container
    │   ├── NavigateScreen.kt       # Depth navigation mode
    │   ├── SettingsScreen.kt       # Preferences UI
    │   └── TextScreen.kt           # OCR mode
    └── theme/
        ├── Color.kt                # Colors + high contrast
        ├── Theme.kt                # Theme with accessibility
        └── Type.kt                 # Typography + large text
```

## Settings

### Audio Settings
- **Speech Rate**: 0.5x - 2.0x (default: 1.0x)
- **Speech Pitch**: 0.5x - 2.0x (default: 1.0x)
- **Test Voice**: Preview current settings

### Feedback Settings
- **Haptic Feedback**: Enable/disable vibration
- **Auto-Read Text**: Automatically read detected text

### Display Settings
- **High Contrast**: Yellow/white on black
- **Large Text**: 50% larger fonts
- **Show Bounding Boxes**: Detection rectangles in Explore mode
- **Show Depth Map**: Grayscale overlay in Navigate mode

### Detection Settings
- **Confidence Threshold**: 10% - 90% (default: 35%)

## Dependencies

- **Jetpack Compose**: UI framework
- **CameraX**: Camera capture
- **ML Kit**: Text recognition
- **ONNX Runtime**: Object detection & depth estimation
- **DataStore**: Preferences storage
- **Navigation Compose**: Screen navigation

## ML Models

| Model | Task | Input | Framework |
|-------|------|-------|-----------|
| SSD MobileNet v1 | Object Detection | 300x300 | ONNX |
| Depth Anything v2 Small | Depth Estimation | 518x518 | ONNX |
| ML Kit Text Recognition | OCR | Variable | ML Kit |

## Build Instructions

### Prerequisites
- Android Studio Ladybug or later
- Android SDK 26+ (target 36)
- JDK 11+

### Build
```bash
# Debug build
./gradlew assembleDebug

# Run tests
./gradlew test
./gradlew connectedAndroidTest

# Install on device
./gradlew installDebug
```

### ONNX Models
Place the following models in `app/src/main/assets/`:
- `ssd_mobilenet_v1.onnx`
- `depth_anything_v2_small.onnx`

## Testing with Accessibility

1. Enable TalkBack: Settings > Accessibility > TalkBack
2. Launch PathSense
3. Swipe to navigate between modes
4. Double-tap to select
5. Verify all elements are announced correctly

## Haptic Patterns

| Pattern | Use Case | Feel |
|---------|----------|------|
| TAP | Selection confirmation | Short buzz |
| DOUBLE_TAP | Mode change | Two quick buzzes |
| WARNING | Medium proximity | Medium pulses |
| ALERT | Near obstacle | Strong rapid pulses |
| SUCCESS | Action completed | Rising pattern |
| DETECTION | Object detected | Quick tick |

## Contributing

1. Fork the repository
2. Create a feature branch
3. Add tests for new functionality
4. Ensure accessibility compliance (48dp targets, content descriptions)
5. Submit a pull request

## License

MIT License - See LICENSE file for details.

## Acknowledgments

- Google Lookout for UX inspiration
- Seeing AI for accessibility patterns
- Depth Anything team for depth estimation model
- ONNX Runtime team for mobile inference
