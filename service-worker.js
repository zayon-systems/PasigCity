// PER Pasig - Pasig Emergency Ready
// Service Worker v1.0 - Network-first for HTML, cache-first for static assets

const CACHE_NAME = 'per-pasig-v1.0';

// All assets to cache on install
const ASSETS = [
  './',
  './index.html',
  './manifest.json',
  './contacts.json',
  './icons/icon-72.png',
  './icons/icon-96.png',
  './icons/icon-128.png',
  './icons/icon-144.png',
  './icons/icon-152.png',
  './icons/icon-192.png',
  './icons/icon-384.png',
  './icons/icon-512.png'
];

// INSTALL - cache everything immediately, activate right away (don't wait for old tabs to close)
self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then(cache => cache.addAll(ASSETS))
      .then(() => self.skipWaiting())
  );
});

// ACTIVATE - remove ALL old caches (any name that isn't this version) and take control immediately
self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys().then(keys =>
      Promise.all(
        keys.filter(k => k !== CACHE_NAME).map(k => caches.delete(k))
      )
    ).then(() => self.clients.claim())
  );
});

// FETCH
// HTML (navigation requests): NETWORK-FIRST. Always try to get the live page
// first so app updates show up immediately; only fall back to the cached
// copy if there's no connection. This is what was causing the app to look
// "stuck" on an old version no matter how many times it was rebuilt.
// Static assets (icons, manifest): CACHE-FIRST, since these rarely change
// and cache-first keeps the app fast and usable offline.
self.addEventListener('fetch', event => {
  if (event.request.method !== 'GET') return;
  if (event.request.url.startsWith('tel:')) return;
  if (event.request.url.startsWith('chrome-extension:')) return;

  const isNavigation = event.request.mode === 'navigate' || /contacts\.json/.test(event.request.url) ||
    (event.request.destination === 'document');

  if (isNavigation) {
    event.respondWith(
      fetch(event.request)
        .then(response => {
          if (response && response.status === 200) {
            const clone = response.clone();
            caches.open(CACHE_NAME).then(cache => cache.put(event.request, clone));
          }
          return response;
        })
        .catch(() => caches.match(event.request, {ignoreSearch: true}).then(r => r || caches.match('./index.html')))
    );
    return;
  }

  event.respondWith(
    caches.match(event.request).then(cachedResponse => {
      if (cachedResponse) {
        fetch(event.request).then(response => {
          if (response && response.status === 200) {
            caches.open(CACHE_NAME).then(cache => cache.put(event.request, response.clone()));
          }
        }).catch(() => {});
        return cachedResponse;
      }
      return fetch(event.request)
        .then(response => {
          if (response && response.status === 200) {
            const clone = response.clone();
            caches.open(CACHE_NAME).then(cache => cache.put(event.request, clone));
          }
          return response;
        })
        .catch(() => caches.match('./index.html'));
    })
  );
});
