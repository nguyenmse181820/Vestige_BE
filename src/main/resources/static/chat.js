class VestigeChat {
    constructor() {
        this.stompClient = null;
        this.currentUser = null;
        this.authToken = null;
        this.conversationId = null;
        this.connected = false;

        this.initializeElements();
        this.attachEventListeners();
    }

    initializeElements() {
        // Form elements
        this.loginContainer = document.getElementById('login-container');
        this.chatContainer = document.getElementById('chat-container');
        this.loginForm = document.getElementById('loginForm');

        // Chat elements
        this.messagesContainer = document.getElementById('messagesContainer');
        this.messageInput = document.getElementById('messageInput');
        this.sendButton = document.getElementById('sendButton');
        this.connectionStatus = document.getElementById('connectionStatus');
        this.currentConversationIdSpan = document.getElementById('currentConversationId');

        // Input elements
        this.usernameInput = document.getElementById('username');
        this.passwordInput = document.getElementById('password');
        this.conversationIdInput = document.getElementById('conversationId');
    }

    attachEventListeners() {
        this.loginForm.addEventListener('submit', (e) => this.handleLogin(e));
        this.sendButton.addEventListener('click', () => this.sendMessage());
        this.messageInput.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') {
                this.sendMessage();
            }
        });
    }

    async handleLogin(event) {
        event.preventDefault();

        const username = this.usernameInput.value.trim();
        const password = this.passwordInput.value.trim();
        const conversationId = this.conversationIdInput.value.trim();

        if (!username || !password || !conversationId) {
            alert('Please fill in all fields');
            return;
        }

        try {
            // Show loading
            this.showLoading('Authenticating...');

            // Authenticate with backend
            const authResponse = await this.authenticate(username, password);

            if (authResponse.accessToken) {
                this.authToken = authResponse.accessToken;
                this.currentUser = username;
                this.conversationId = parseInt(conversationId);

                // Show chat interface
                this.loginContainer.classList.add('hidden');
                this.chatContainer.classList.remove('hidden');
                this.currentConversationIdSpan.textContent = conversationId;

                // Connect to WebSocket
                this.connectWebSocket();

                // Load existing messages
                await this.loadMessages();
            }
        } catch (error) {
            console.error('Login error:', error);
            alert('Login failed: ' + error.message);
        }
    }

    async authenticate(username, password) {
        const response = await fetch('/api/auth/login', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                username: username,
                password: password
            })
        });

        if (!response.ok) {
            const errorData = await response.json();
            throw new Error(errorData.message || 'Authentication failed');
        }

        return await response.json();
    }

    connectWebSocket() {
        const socket = new SockJS('/ws');
        this.stompClient = Stomp.over(socket);

        // Disable debug logging
        this.stompClient.debug = null;

        // Add authorization header
        const headers = {
            'Authorization': 'Bearer ' + this.authToken
        };

        this.stompClient.connect(headers,
            () => this.onConnected(),
            (error) => this.onError(error)
        );
    }

    onConnected() {
        console.log('Connected to WebSocket');
        this.connected = true;
        this.updateConnectionStatus('connected');
        this.sendButton.disabled = false;

        // Subscribe to conversation messages
        this.stompClient.subscribe(`/topic/conversation/${this.conversationId}`,
            (message) => this.onMessageReceived(message)
        );

        // Subscribe to read receipts
        this.stompClient.subscribe(`/topic/conversation/${this.conversationId}/read`,
            (message) => this.onReadReceipt(message)
        );

        // Join the conversation
        this.stompClient.send(`/app/chat.join/${this.conversationId}`, {}, JSON.stringify({}));
    }

    onError(error) {
        console.error('WebSocket connection error:', error);
        this.connected = false;
        this.updateConnectionStatus('disconnected');
        this.sendButton.disabled = true;

        // Try to reconnect after 3 seconds
        setTimeout(() => {
            if (!this.connected) {
                console.log('Attempting to reconnect...');
                this.connectWebSocket();
            }
        }, 3000);
    }

    onMessageReceived(payload) {
        const message = JSON.parse(payload.body);
        this.displayMessage(message);
        this.scrollToBottom();

        // Play notification sound for received messages
        if (message.senderUsername !== this.currentUser && message.type === 'CHAT') {
            this.playNotificationSound();
        }
    }

    onReadReceipt(payload) {
        const receipt = JSON.parse(payload.body);
        console.log('Message read by:', receipt.readBy);
        // You can add visual indicators for read messages here
        this.showReadReceipt(receipt);
    }

    async loadMessages() {
        try {
            this.showLoading('Loading messages...');

            const response = await fetch(`/api/chat/conversations/${this.conversationId}/messages/recent`, {
                headers: {
                    'Authorization': 'Bearer ' + this.authToken
                }
            });

            if (response.ok) {
                const result = await response.json();
                const messages = result.data || [];

                // Clear existing messages
                this.messagesContainer.innerHTML = '';

                // Display messages
                messages.reverse().forEach(message => this.displayMessage(message));
                this.scrollToBottom();
            } else {
                throw new Error('Failed to load messages');
            }
        } catch (error) {
            console.error('Error loading messages:', error);
            this.showError('Failed to load messages');
        }
    }

    sendMessage() {
        const content = this.messageInput.value.trim();

        if (!content || !this.connected) {
            return;
        }

        const message = {
            conversationId: this.conversationId,
            content: content,
            type: 'CHAT'
        };

        try {
            this.stompClient.send(`/app/chat.send/${this.conversationId}`, {}, JSON.stringify(message));
            this.messageInput.value = '';
        } catch (error) {
            console.error('Error sending message:', error);
            this.showError('Failed to send message');
        }
    }

    displayMessage(message) {
        const messageElement = document.createElement('div');
        messageElement.className = 'message';
        messageElement.setAttribute('data-message-id', message.messageId);

        // Determine message type and styling
        if (message.type === 'JOIN' || message.type === 'LEAVE' || message.type === 'SYSTEM') {
            messageElement.classList.add('system');
        } else if (message.senderUsername === this.currentUser) {
            messageElement.classList.add('sent');
        } else {
            messageElement.classList.add('received');
        }

        const bubble = document.createElement('div');
        bubble.className = 'message-bubble';
        bubble.textContent = message.content;

        const info = document.createElement('div');
        info.className = 'message-info';

        if (message.type === 'CHAT') {
            const time = new Date(message.createdAt).toLocaleTimeString();
            if (message.senderUsername === this.currentUser) {
                info.textContent = `You • ${time}`;
                if (message.isRead) {
                    info.textContent += ' • ✓✓';
                } else {
                    info.textContent += ' • ✓';
                }
            } else {
                info.textContent = `${message.senderName || message.senderUsername} • ${time}`;
            }
        } else {
            info.textContent = new Date(message.createdAt).toLocaleTimeString();
        }

        messageElement.appendChild(bubble);
        messageElement.appendChild(info);
        this.messagesContainer.appendChild(messageElement);
    }

    scrollToBottom() {
        this.messagesContainer.scrollTop = this.messagesContainer.scrollHeight;
    }

    updateConnectionStatus(status) {
        this.connectionStatus.textContent = status === 'connected' ? 'Connected' : 'Disconnected';
        this.connectionStatus.className = `connection-status ${status}`;
    }

    showLoading(message) {
        // Create or update loading message
        let loadingEl = document.getElementById('loading-message');
        if (!loadingEl) {
            loadingEl = document.createElement('div');
            loadingEl.id = 'loading-message';
            loadingEl.style.cssText = `
                position: fixed;
                top: 50%;
                left: 50%;
                transform: translate(-50%, -50%);
                background: rgba(0,0,0,0.8);
                color: white;
                padding: 10px 20px;
                border-radius: 5px;
                z-index: 1000;
            `;
            document.body.appendChild(loadingEl);
        }
        loadingEl.textContent = message;

        // Auto-hide after 5 seconds
        setTimeout(() => {
            if (loadingEl) {
                loadingEl.remove();
            }
        }, 5000);
    }

    showError(message) {
        // Create error notification
        const errorEl = document.createElement('div');
        errorEl.style.cssText = `
            position: fixed;
            top: 20px;
            right: 20px;
            background: #f44336;
            color: white;
            padding: 10px 20px;
            border-radius: 5px;
            z-index: 1000;
            animation: slideInRight 0.3s ease-out;
        `;
        errorEl.textContent = message;
        document.body.appendChild(errorEl);

        // Auto-remove after 3 seconds
        setTimeout(() => {
            errorEl.remove();
        }, 3000);
    }

    showReadReceipt(receipt) {
        // Find messages sent by current user and mark as read
        const messageElements = this.messagesContainer.querySelectorAll('.message.sent');
        messageElements.forEach(el => {
            const info = el.querySelector('.message-info');
            if (info && !info.textContent.includes('✓✓')) {
                info.textContent = info.textContent.replace(' • ✓', ' • ✓✓');
            }
        });
    }

    playNotificationSound() {
        // Simple notification sound using Web Audio API
        try {
            const audioContext = new (window.AudioContext || window.webkitAudioContext)();
            const oscillator = audioContext.createOscillator();
            const gainNode = audioContext.createGain();

            oscillator.connect(gainNode);
            gainNode.connect(audioContext.destination);

            oscillator.frequency.value = 800;
            oscillator.type = 'sine';
            gainNode.gain.value = 0.1;

            oscillator.start();
            oscillator.stop(audioContext.currentTime + 0.1);
        } catch (error) {
            console.log('Could not play notification sound:', error);
        }
    }

    // Method to mark messages as read when user scrolls to bottom
    markMessagesAsRead() {
        if (this.connected && this.stompClient) {
            this.stompClient.send(`/app/chat.markRead/${this.conversationId}`, {}, JSON.stringify({}));
        }
    }

    // Add scroll listener to mark messages as read
    addScrollListener() {
        this.messagesContainer.addEventListener('scroll', () => {
            const { scrollTop, scrollHeight, clientHeight } = this.messagesContainer;
            const isAtBottom = scrollTop + clientHeight >= scrollHeight - 10;

            if (isAtBottom) {
                this.markMessagesAsRead();
            }
        });
    }
}

// Initialize chat when page loads
document.addEventListener('DOMContentLoaded', () => {
    const chat = new VestigeChat();
    chat.addScrollListener();

    // Add CSS animations
    const style = document.createElement('style');
    style.textContent = `
        @keyframes slideInRight {
            from {
                transform: translateX(100%);
                opacity: 0;
            }
            to {
                transform: translateX(0);
                opacity: 1;
            }
        }
    `;
    document.head.appendChild(style);
});