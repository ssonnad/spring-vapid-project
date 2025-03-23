# Spring Project With Web Push Notifications

This project demonstrates the implementation of Web Push Notifications using Spring Boot and the VAPID protocol.

## Project Overview

This application implements a web push notification system using:

### Core Features
- **VAPID Protocol**: Secure web push notification delivery
- **Email-based Subscriptions**: Subscribe and manage notifications per email
- **Thread-safe Implementation**: Concurrent handling of subscriptions
- **Service Worker Integration**: Background notification processing

### Technical Stack
- **Backend**: Spring Boot 3.x
- **Push Notifications**: Web Push API
- **Security**: VAPID protocol with public/private key encryption
- **Concurrency**: Thread-safe collections (ConcurrentHashMap, CopyOnWriteArrayList)

### Key Components
1. **VapidController**: REST endpoints for subscription management
2. **SubscriptionService**: Thread-safe subscription handling
3. **Service Worker**: Client-side push notification handling
4. **Web Interface**: User subscription management

### Architecture
```
Client <--> VAPID Protocol <--> Push Service <--> Browser
  ↑                              ↑
  |                             |
  +----------------------------->
     REST API Endpoints
```

## Setup Instructions

1. **Clone the repository:**
   ```bash
   git clone <repository-url>
   cd spring-ai-project
   ```

2. **Build the project:**
   ```bash
   mvn clean install
   ```

3. **Run the application:**
   ```bash
   mvn spring-boot:run
   ```

## Usage

- The REST API can be accessed at `http://localhost:8080/api`.
- Use tools like Postman or curl to interact with the API endpoints defined in `VapidController.java`.

## Testing Thread Safety

### Prerequisites
- Install Apache Bench (ab) for load testing:
```bash
brew install apache2
```

### Subscription Load Testing

1. Create a test payload file:
```bash
cat > subscription.json << EOL
{
    "endpoint": "https://example.com/push/123",
    "keys": {
        "p256dh": "test_public_key",
        "auth": "test_auth_secret"
    }
}
EOL
```

2. Run concurrent subscription tests:
```bash
# Test with 1000 requests, 10 concurrent
ab -n 1000 -c 10 -T 'application/json' -p subscription.json \
   http://localhost:8080/vapid/subscribe/test@example.com
```

# Create the push.json file
```bash
cat > push.json << EOL
{
    "message": "Test push notification message",
    "emails": [
        "test1@example.com",
        "test2@example.com"
    ]
}
EOL
```

3. Run concurrent push notification tests:
```bash
# Test with 500 requests, 5 concurrent
ab -n 500 -c 5 -T 'application/json' -p push.json \
   http://localhost:8080/vapid/push
```

### Monitor Results

- Check server logs for any concurrent modification exceptions
- Verify subscription counts remain consistent
- Monitor memory usage during load testing:
```bash
top -pid $(pgrep -f spring-ai-project)
```

### Thread Safety Implementation

The application uses thread-safe collections:
- `ConcurrentHashMap` for storing subscriptions
- `CopyOnWriteArrayList` for subscription lists
- Atomic operations for updates
- Defensive copying for collection returns

## Contributing

Contributions are welcome! Please open an issue or submit a pull request for any enhancements or bug fixes.

## License

This project is licensed under the MIT License. See the LICENSE file for more details.