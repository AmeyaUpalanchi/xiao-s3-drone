const { contextBridge, ipcRenderer, webUtils } = require('electron');

contextBridge.exposeInMainWorld('tdc', {
  settings: () => ipcRenderer.invoke('settings:get'),
  saveSettings: (s) => ipcRenderer.invoke('settings:set', s),

  get: (p) => ipcRenderer.invoke('drone:get', p),
  post: (p, body, timeout) => ipcRenderer.invoke('drone:post', p, body, timeout),
  ota: (filePath) => ipcRenderer.invoke('drone:ota', filePath),
  onOtaProgress: (fn) => {
    const h = (_e, sent, total) => fn(sent, total);
    ipcRenderer.on('ota:progress', h);
    return () => ipcRenderer.removeListener('ota:progress', h);
  },
  capture: () => ipcRenderer.invoke('drone:capture'),

  pickFile: (kind) => ipcRenderer.invoke('file:pick', kind),
  describeFile: (p) => ipcRenderer.invoke('file:describe', p),
  readFile: (p) => ipcRenderer.invoke('file:read', p),
  pathOf: (file) => webUtils.getPathForFile(file),

  latest: () => ipcRenderer.invoke('gh:latest'),
  firmware: (version, ios) => ipcRenderer.invoke('gh:firmware', version, ios),

  openExternal: (url) => ipcRenderer.invoke('open:external', url),
  showFile: (p) => ipcRenderer.invoke('open:path', p),
  platform: process.platform,
});
