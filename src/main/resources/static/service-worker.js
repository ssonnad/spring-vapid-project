self.addEventListener('push', function(event) {
    console.log('Push received:', event.data.text());
    
    let pushData;
    try {
        pushData = event.data.json();
    } catch (e) {
        pushData = {
            title: 'Push Notification',
            message: event.data.text()
        };
    }
    
    const options = {
        body: pushData.message || pushData,
        icon: '/images/icon.jpeg',
        badge: '/images/badge.jpeg',
        image: '/images/icon.jpeg',
        silent: false,
        requireInteraction: true,
        data: {
            dateOfArrival: Date.now(),
            primaryKey: 1,
            url: pushData.url || '/'
        },
        actions: [
            {
                action: 'open',
                title: 'Open App'
            }
        ]
    };

    event.waitUntil(
        // Check if icons exist before showing notification
        Promise.all([
            fetch(options.icon).catch(() => {
                console.warn('Icon failed to load');
                delete options.icon;
            }),
            fetch(options.badge).catch(() => {
                console.warn('Badge failed to load');
                delete options.badge;
            })
        ]).then(() => {
            return self.registration.showNotification(
                pushData.title || 'Push Notification', 
                options
            );
        })
    );
});

self.addEventListener('notificationclick', function(event) {
    console.log('Notification clicked:', event);
    event.notification.close();

    const urlToOpen = event.notification.data.url || '/';

    event.waitUntil(
        clients.matchAll({
            type: 'window',
            includeUncontrolled: true
        }).then(function(clientList) {
            // If there's an existing window, focus it
            for (let client of clientList) {
                if (client.url === urlToOpen && 'focus' in client) {
                    return client.focus();
                }
            }
            // If no existing window, open a new one
            return clients.openWindow(urlToOpen);
        })
    );
});