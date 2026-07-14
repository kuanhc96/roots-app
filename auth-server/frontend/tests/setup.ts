// happy-dom does not implement the VisualViewport API, which Vuetify's overlay
// positioning (v-snackbar, v-menu, ...) reads. A minimal stand-in is enough.
if (!window.visualViewport) {
  const visualViewport = Object.assign(new EventTarget(), {
    width: 1280,
    height: 800,
    offsetLeft: 0,
    offsetTop: 0,
    pageLeft: 0,
    pageTop: 0,
    scale: 1,
  })
  Object.defineProperty(window, 'visualViewport', { value: visualViewport })
}
