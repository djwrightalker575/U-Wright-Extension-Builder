chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (!message || message.type !== 'WIRE_ANDROID_REQUEST_V1') return;
  sendResponse({ok: true, note: 'Scaffold only in v0.1; native Chromium executor lands in Titanium port.'});
  return true;
});
