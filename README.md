# Android GPS Tracking with APRS Integration

![aprs-gps-app-icon](https://github.com/user-attachments/assets/7dafb3e9-38ac-493c-b762-5e121c57bdc3)
## An Android application that combines modern GPS tracking capabilities with the Amateur Position Reporting System (APRS) network.
- #### Track and visualize their real-time location on Google Maps
- #### Transmit custom messages to APRS-IS network
- ####  Navigate to specified coordinates with distance reporting to APRS network

### Location Tracking & Visualization
- Live GPS location tracking with Google Maps integration
- Real-time speed, bearing, and road name display
- Trail visualization shows live tracking
- Direction determination based on bearing

### APRS Integration
- Automatic connection to APRS-IS servers
- Standard-compliant APRS packet formatting
- Configurable update intervals
- TCP/IP APRS-IS authentication & communication
- Custom APRS network message
  
### AWS Cloud Integration
- API Gateway REST API
- JSON-based position passed to Lambda
- Real-time data updates to AWS RDS

### Navigation Capabilities
- Support for destination coordinates via text input
- Haversine distance calculation to target
- Update APRS message in real time with distance to destination

## Technical Implementation

### Key Components

- **`AprsService`**: Manages APRS packet creation + transmission
- **`AwsService`**: Handles AWS API communication & data persistence
- **`MapsActivity`**: Controls the user interface & map visualization
- **`LocationRequest`**: Configures high-precision location updates

### Core Technologies

- **Kotlin**: 100% Kotlin usage with modern language standards implemented
- **Android Location Services**: FusedLocationProvider consumes battery efficiently
- **Google Maps SDK**: Live mapping & visualization
- **Foreground Services**: Background operations
- **OkHttp**: HTTP client for API communication
- **APRS Protocol**: Standards compliant implementation for APRS radio network integration

### Architecture Features

- Modular UI, location services, and data transmission
- Efficient background processing & proper lifecycle management
- Eerror handling and reconnection
- Configurable BuildConfig parameters

![aprs.fi-trail-screenshot](https://github.com/user-attachments/assets/c6cb7868-5737-444b-8e45-e362b86df016)

