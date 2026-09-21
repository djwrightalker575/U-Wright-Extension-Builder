(() => {
  if (window.__wireTitaniumObserver) return;
  window.__wireTitaniumObserver = true;
  const seen = new Set();
  const scan = () => {
    document.querySelectorAll('pre').forEach(pre => {
      const text = (pre.innerText || '').trim();
      if (!text.startsWith('WIRE_ANDROID_REQUEST_V1')) return;
      const key = text.slice(0, 512);
      if (seen.has(key)) return;
      seen.add(key);
      chrome.runtime.sendMessage({type:'WIRE_ANDROID_REQUEST_V1', payload:text});
    });
  };
  new MutationObserver(scan).observe(document.documentElement, {subtree:true, childList:true, characterData:true});
  scan();
})();
