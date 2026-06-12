const chokidar = require('chokidar');
const axios = require('axios');
const path = require('path');

// Locate parent workspace directory
const WORKSPACE_DIR = path.resolve(__dirname, '..');

// Strict ignore list to minimize CPU usage and prevent infinite loops
const IGNORED_PATHS = [
  'node_modules', '.git', 'dist', 'build', 'target', 
  'backend/target', '.vscode', '.idea', 'agentguard.db'
];

console.log(`[AgentGuard Watcher] Initiating monitoring on: ${WORKSPACE_DIR}`);
console.log(`[AgentGuard Watcher] Excluded directories: ${IGNORED_PATHS.join(', ')}`);

const watcher = chokidar.watch(WORKSPACE_DIR, {
  ignored: IGNORED_PATHS.map(p => `**/${p}/**`),
  persistent: true,
  ignoreInitial: true,
  depth: 99
});

// 300ms Debounce Layer Map
const debounceBuffer = new Map();

// Deduplication History Cache (stores relative path -> { event, time })
const deduplicationCache = new Map();

watcher.on('all', (event, filePath) => {
  const relativePath = path.relative(WORKSPACE_DIR, filePath);
  const now = Date.now();

  // Deduplication Check: Ignore identical event within 300ms window
  const lastEvent = deduplicationCache.get(relativePath);
  if (lastEvent && lastEvent.event === event && (now - lastEvent.timestamp) < 300) {
    // Suppress duplicate spam
    return;
  }

  // Update Cache immediately to suppress rapid concurrent bursts
  deduplicationCache.set(relativePath, { event, timestamp: now });

  if (debounceBuffer.has(relativePath)) {
    clearTimeout(debounceBuffer.get(relativePath));
  }

  // 300ms Debouncing Delay
  const timeoutId = setTimeout(() => {
    debounceBuffer.delete(relativePath);

    const payload = {
      type: 'FILE_MODIFIED',
      event: event.toUpperCase(),
      path: relativePath,
      session_id: process.env.AGENTGUARD_SESSION_ID || 'development-human',
      timestamp: new Date().toISOString()
    };

    console.log(`[AgentGuard Watcher] Debounced file event: ${event} -> ${relativePath}`);
    sendTelemetry(payload);
  }, 300);

  debounceBuffer.set(relativePath, timeoutId);
});

// Periodic cache cleanup (every 10 seconds) to prevent memory leak
setInterval(() => {
  const now = Date.now();
  for (const [key, value] of deduplicationCache.entries()) {
    if (now - value.timestamp > 5000) {
      deduplicationCache.delete(key);
    }
  }
}, 10000);

function sendTelemetry(payload) {
  const sessionId = process.env.AGENTGUARD_SESSION_ID || 'development-human';
  
  axios.post('http://localhost:8080/api/v1/events/telemetry', payload, {
    headers: {
      'Content-Type': 'application/json',
      'X-Session-ID': sessionId
    }
  }).catch((err) => {
    // Silent fallback when backend is offline
    console.log(`[AgentGuard Watcher] Failed to dispatch event to backend (Offline): ${err.message}`);
  });
}

// Keep daemon running safely
process.on('SIGINT', () => {
  watcher.close().then(() => {
    console.log('[AgentGuard Watcher] Monitoring terminated safely.');
    process.exit(0);
  });
});
